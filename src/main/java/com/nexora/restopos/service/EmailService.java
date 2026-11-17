package com.nexora.restopos.service;

import com.nexora.restopos.model.Cuenta;
import com.nexora.restopos.model.MailConfig;
import com.nexora.restopos.model.Tienda;
import com.nexora.restopos.model.User;
import com.nexora.restopos.repository.CuentaRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.util.Properties;
import java.util.function.Supplier;

/**
 * Envío de correos del sistema: ticket de una cuenta en PDF al cliente, reporte de cierre de
 * caja en Excel al administrador de la tienda, contraseña temporal de un usuario nuevo,
 * recuperación de contraseña, y avisos a clientes de la página web (verificación de correo,
 * restablecer contraseña, reservaciones).
 *
 * <p>La cuenta SMTP con la que sale TODO se lee de {@link MailConfig} (vía {@link
 * MailConfigService#getEntity()}) en cada envío — así un SUPER_ADMIN puede rotar la
 * contraseña o cambiar de cuenta desde "Correo del sistema" sin reiniciar el backend. Si el
 * envío está deshabilitado ahí, el correo simplemente se omite (queda en el log). El "De:"
 * siempre es esa cuenta (Gmail no permite otro remitente); lo que cambia es el nombre
 * visible: el de la tienda en los correos de una tienda, y {@value #SYSTEM_SENDER_NAME} en
 * los de cuenta/plataforma.</p>
 *
 * <p>Todos los envíos son asíncronos ({@code @Async}) y "best effort": un fallo de correo solo
 * se registra en el log y nunca revierte ni bloquea la operación de negocio que lo disparó.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    // Nombre visible de los correos de cuenta/plataforma (contraseña temporal, recuperación),
    // que no son una operación de ninguna tienda en particular.
    private static final String SYSTEM_SENDER_NAME = "Nexora Systems Soporte";

    private final MailConfigService mailConfigService;
    private final TicketPdfService ticketPdfService;
    private final CuentaRepository cuentaRepository;

    /** Mensaje en construcción: el helper ya trae remitente; aquí solo se llenan destinatario, asunto, etc. */
    @FunctionalInterface
    private interface MessageFiller {
        void fill(MimeMessageHelper helper) throws MessagingException;
    }

    /** Arma un {@link JavaMailSender} de un solo uso con las credenciales vigentes en {@link MailConfig}. */
    private JavaMailSender buildSender(MailConfig cfg) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(cfg.getSmtpHost());
        sender.setPort(cfg.getSmtpPort());
        sender.setUsername(cfg.getSmtpUsername());
        sender.setPassword(cfg.getSmtpPassword());
        sender.setDefaultEncoding("UTF-8");
        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        // 465 es SMTPS (SSL directo); cualquier otro puerto (587, 25...) usa STARTTLS.
        if (cfg.getSmtpPort() != null && cfg.getSmtpPort() == 465) {
            props.put("mail.smtp.ssl.enable", "true");
        } else {
            props.put("mail.smtp.starttls.enable", "true");
        }
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "15000");
        props.put("mail.smtp.writetimeout", "15000");
        return sender;
    }

    /**
     * Envía un correo con la cuenta vigente de {@link MailConfig}.
     *
     * @param descripcion texto corto para el log (ej. "ticket de la cuenta 12")
     * @param toEmail destinatario
     * @param senderName nombre visible del remitente (nombre de la tienda o del sistema)
     * @param multipart {@code true} si lleva adjuntos
     * @param filler llena destinatario/asunto/texto/adjuntos
     * @return {@code true} si se envió; {@code false} si está deshabilitado o falló
     */
    private boolean send(String descripcion, String toEmail, String senderName, boolean multipart, MessageFiller filler) {
        MailConfig cfg;
        try {
            cfg = mailConfigService.getEntity();
        } catch (RuntimeException e) {
            log.error("No se pudo leer la configuración de correo; se omite {} a {}: {}", descripcion, toEmail, e.getMessage());
            return false;
        }
        if (!Boolean.TRUE.equals(cfg.getEnabled())) {
            log.info("Envío de correo deshabilitado (Correo del sistema); se omite {} a {}", descripcion, toEmail);
            return false;
        }
        if (cfg.getSmtpUsername() == null || cfg.getSmtpUsername().isBlank()) {
            log.warn("Correo del sistema sin cuenta configurada; se omite {} a {}", descripcion, toEmail);
            return false;
        }
        try {
            JavaMailSender sender = buildSender(cfg);
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, multipart, "UTF-8");
            try {
                helper.setFrom(new InternetAddress(cfg.getSmtpUsername(), senderName, "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                helper.setFrom(cfg.getSmtpUsername());
            }
            helper.setTo(toEmail);
            filler.fill(helper);
            sender.send(message);
            log.info("Correo enviado: {} a {}", descripcion, toEmail);
            return true;
        } catch (MessagingException | RuntimeException e) {
            log.error("No se pudo enviar {} a {}: {}", descripcion, toEmail, e.getMessage());
            return false;
        }
    }

    /** Nombre de la tienda para el remitente visible; si no se puede leer, el del sistema. */
    private static String senderName(Supplier<String> tiendaName) {
        try {
            String name = tiendaName.get();
            return name != null && !name.isBlank() ? name : SYSTEM_SENDER_NAME;
        } catch (RuntimeException e) {
            return SYSTEM_SENDER_NAME;
        }
    }

    @Async
    public void sendTicketEmail(Cuenta cuenta, String toEmail) {
        byte[] pdf;
        try {
            pdf = ticketPdfService.generate(cuenta);
        } catch (RuntimeException e) {
            log.error("No se pudo generar el ticket de la cuenta {}: {}", cuenta.getId(), e.getMessage());
            return;
        }
        String folio = cuenta.getTicketFolio() != null ? cuenta.getTicketFolio() : "#" + cuenta.getId();
        boolean ok = send("ticket de la cuenta " + cuenta.getId(), toEmail,
                senderName(() -> cuenta.getOrden().getTienda().getName()), true, helper -> {
                    helper.setSubject("Tu ticket " + folio);
                    helper.setText("Gracias por tu visita. Adjuntamos tu ticket en PDF.");
                    helper.addAttachment("ticket-" + cuenta.getId() + ".pdf", new ByteArrayResource(pdf));
                });
        if (ok) {
            cuenta.setTicketEmailEnviado(true);
            cuentaRepository.save(cuenta);
        }
    }

    @Async
    public void sendCashCutReportEmail(Tienda tienda, int cutCount, byte[] excelBytes, String toEmail) {
        send("reporte de cierre de caja de " + tienda.getName(), toEmail, senderName(tienda::getName), true, helper -> {
            helper.setSubject("Reporte de cierre de caja — " + tienda.getName());
            helper.setText(
                    "Se cerraron " + cutCount + " corte(s) de caja en " + tienda.getName() + ".\n\n" +
                    "Adjuntamos el reporte detallado en Excel."
            );
            helper.addAttachment("cierre-caja-" + tienda.getId() + ".xlsx", new ByteArrayResource(excelBytes));
        });
    }

    /**
     * Envía el reporte de fin de día de UNA caja (un correo, un Excel con sus cortes del día).
     *
     * @param registerName nombre de la caja, para el asunto y el texto del correo
     */
    @Async
    public void sendCashRegisterDayReportEmail(Tienda tienda, String registerName, int cutCount, byte[] excelBytes, String toEmail) {
        send("fin de día de " + tienda.getName() + " / " + registerName, toEmail, senderName(tienda::getName), true, helper -> {
            helper.setSubject("Corte de fin de día — " + registerName + " · " + tienda.getName());
            helper.setText(
                    "Se hizo el corte de fin de día de " + registerName + " en " + tienda.getName() + ".\n" +
                    "Incluye " + cutCount + " corte(s) del día (traspasos y corte final).\n\n" +
                    "Adjuntamos el reporte detallado en Excel."
            );
            String safeName = registerName.replaceAll("[^A-Za-z0-9_-]+", "-");
            helper.addAttachment("fin-de-dia-" + safeName + "-" + java.time.LocalDate.now() + ".xlsx",
                    new ByteArrayResource(excelBytes));
        });
    }

    /**
     * Envía por correo la contraseña temporal de un usuario recién dado de alta (o al que se
     * le reseteó la contraseña), junto con el aviso de que se le va a pedir cambiarla.
     *
     * @param user usuario que recibió la contraseña temporal
     * @param tempPassword la contraseña temporal en texto plano (nunca se guarda así, solo se envía)
     * @param loginUrl URL de la pantalla de login del frontend, incluida en el correo
     */
    @Async
    public void sendNewUserPasswordEmail(User user, String tempPassword, String loginUrl) {
        send("contraseña temporal (usuario nuevo)", user.getEmail(), SYSTEM_SENDER_NAME, false, helper -> {
            helper.setSubject("Tu cuenta en RestoPOS");
            helper.setText(
                    "Hola " + user.getName() + ",\n\n" +
                    "Se creó una cuenta para ti en el sistema. Estos son tus datos de acceso:\n\n" +
                    "Correo: " + user.getEmail() + "\n" +
                    "Contraseña temporal: " + tempPassword + "\n\n" +
                    "Inicia sesión aquí:\n" + loginUrl + "\n\n" +
                    "Al iniciar sesión por primera vez se te va a pedir que la cambies por una de tu elección.\n\n" +
                    "Si tú no esperabas este correo, contacta a tu administrador."
            );
        });
    }

    /** Enlace para confirmar el correo de un cliente de la página web (válido 48 h). */
    @Async
    public void sendClienteVerificacionEmail(String toEmail, String nombre, String tienda, String link) {
        send("verificación de correo de cliente", toEmail, senderName(() -> tienda), false, helper -> {
            helper.setSubject("Confirma tu correo — " + tienda);
            helper.setText(
                    "Hola " + nombre + ",\n\n" +
                    "Gracias por registrarte en " + tienda + ". Confirma tu correo para poder hacer pedidos con tu cuenta " +
                    "(el enlace es válido por 48 horas):\n\n" + link + "\n\n" +
                    "Si tú no creaste esta cuenta, ignora este correo.");
        });
    }

    /** Enlace para que un cliente de la página web restablezca su contraseña (válido 1 h, un solo uso). */
    @Async
    public void sendClienteRestablecerEmail(String toEmail, String nombre, String tienda, String link) {
        send("restablecer contraseña de cliente", toEmail, senderName(() -> tienda), false, helper -> {
            helper.setSubject("Restablece tu contraseña — " + tienda);
            helper.setText(
                    "Hola " + nombre + ",\n\n" +
                    "Recibimos una solicitud para restablecer tu contraseña en " + tienda + ". El enlace es válido por 1 hora " +
                    "y solo se puede usar una vez:\n\n" + link + "\n\n" +
                    "Si tú no lo pediste, ignora este correo: tu contraseña sigue siendo la misma.");
        });
    }

    /** Aviso al cliente sobre su reservación de mesa (confirmada, rechazada, cancelada). */
    @Async
    public void sendReservacionEmail(String toEmail, String asunto, String texto) {
        send("aviso de reservación \"" + asunto + "\"", toEmail, SYSTEM_SENDER_NAME, false, helper -> {
            helper.setSubject(asunto);
            helper.setText(texto);
        });
    }

    /**
     * Envía el link para restablecer contraseña del flujo "olvidé mi contraseña" (ver
     * {@code AuthService#forgotPassword}). El link ya trae el token de un solo uso; vigente
     * 30 minutos.
     */
    @Async
    public void sendPasswordResetEmail(User user, String resetLink) {
        send("recuperación de contraseña", user.getEmail(), SYSTEM_SENDER_NAME, false, helper -> {
            helper.setSubject("Recupera tu contraseña");
            helper.setText(
                    "Hola " + user.getName() + ",\n\n" +
                    "Recibimos una solicitud para restablecer tu contraseña. Este link es válido por 30 minutos:\n\n" +
                    resetLink + "\n\n" +
                    "Si tú no pediste esto, puedes ignorar este correo — tu contraseña sigue siendo la misma."
            );
        });
    }
}
