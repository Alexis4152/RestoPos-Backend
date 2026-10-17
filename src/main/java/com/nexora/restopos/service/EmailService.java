package com.nexora.restopos.service;

import com.nexora.restopos.model.Cuenta;
import com.nexora.restopos.model.Tienda;
import com.nexora.restopos.model.User;
import com.nexora.restopos.repository.CuentaRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Envío de correos del sistema: ticket de una cuenta en PDF al cliente, reporte de cierre de
 * caja en Excel al administrador de la tienda, y la contraseña temporal de un usuario recién
 * dado de alta.
 *
 * <p>Todos los envíos son asíncronos ({@code @Async}) y "best effort": un fallo de correo solo
 * se registra en el log y nunca revierte ni bloquea la operación de negocio que lo disparó (el
 * pago ya quedó registrado, el corte ya quedó cerrado, el usuario ya quedó creado).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final TicketPdfService ticketPdfService;
    private final CuentaRepository cuentaRepository;

    @Async
    public void sendTicketEmail(Cuenta cuenta, String toEmail) {
        try {
            byte[] pdf = ticketPdfService.generate(cuenta);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(toEmail);
            helper.setSubject("Tu ticket " + (cuenta.getTicketFolio() != null ? cuenta.getTicketFolio() : "#" + cuenta.getId()));
            helper.setText("Gracias por tu visita. Adjuntamos tu ticket en PDF.");
            helper.addAttachment("ticket-" + cuenta.getId() + ".pdf", new org.springframework.core.io.ByteArrayResource(pdf));

            mailSender.send(message);
            log.info("Ticket de la cuenta {} enviado a {}", cuenta.getId(), toEmail);
            cuenta.setTicketEmailEnviado(true);
            cuentaRepository.save(cuenta);
        } catch (MessagingException | RuntimeException e) {
            log.error("No se pudo enviar el ticket de la cuenta {} a {}: {}", cuenta.getId(), toEmail, e.getMessage());
        }
    }

    @Async
    public void sendCashCutReportEmail(Tienda tienda, int cutCount, byte[] excelBytes, String toEmail) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(toEmail);
            helper.setSubject("Reporte de cierre de caja — " + tienda.getName());
            helper.setText(
                    "Se cerraron " + cutCount + " corte(s) de caja en " + tienda.getName() + ".\n\n" +
                    "Adjuntamos el reporte detallado en Excel."
            );
            helper.addAttachment("cierre-caja-" + tienda.getId() + ".xlsx",
                    new org.springframework.core.io.ByteArrayResource(excelBytes));

            mailSender.send(message);
            log.info("Reporte de cierre de caja de {} ({} corte(s)) enviado a {}", tienda.getName(), cutCount, toEmail);
        } catch (MessagingException | RuntimeException e) {
            log.error("No se pudo enviar el reporte de cierre de {} a {}: {}", tienda.getName(), toEmail, e.getMessage());
        }
    }

    /**
     * Envía el reporte de fin de día de UNA caja (un correo, un Excel con sus cortes del día).
     *
     * @param registerName nombre de la caja, para el asunto y el texto del correo
     */
    @Async
    public void sendCashRegisterDayReportEmail(Tienda tienda, String registerName, int cutCount, byte[] excelBytes, String toEmail) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(toEmail);
            helper.setSubject("Corte de fin de día — " + registerName + " · " + tienda.getName());
            helper.setText(
                    "Se hizo el corte de fin de día de " + registerName + " en " + tienda.getName() + ".\n" +
                    "Incluye " + cutCount + " corte(s) del día (traspasos y corte final).\n\n" +
                    "Adjuntamos el reporte detallado en Excel."
            );
            String safeName = registerName.replaceAll("[^A-Za-z0-9_-]+", "-");
            helper.addAttachment("fin-de-dia-" + safeName + "-" + java.time.LocalDate.now() + ".xlsx",
                    new org.springframework.core.io.ByteArrayResource(excelBytes));
            mailSender.send(message);
            log.info("Fin de día de {} / {} ({} corte(s)) enviado a {}", tienda.getName(), registerName, cutCount, toEmail);
        } catch (MessagingException | RuntimeException e) {
            log.error("No se pudo enviar el fin de día de {} / {} a {}: {}", tienda.getName(), registerName, toEmail, e.getMessage());
        }
    }

    /**
     * Envía por correo la contraseña temporal de un usuario recién dado de alta (o al que
     * se le reseteó la contraseña), junto con el aviso de que se le va a pedir cambiarla al
     * iniciar sesión (ver {@link User#getMustChangePassword()}).
     *
     * @param user usuario que recibió la contraseña temporal
     * @param tempPassword la contraseña temporal en texto plano (nunca se guarda así, solo se envía)
     * @param loginUrl URL de la pantalla de login del frontend, incluida en el correo
     */
    @Async
    public void sendNewUserPasswordEmail(User user, String tempPassword, String loginUrl) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(user.getEmail());
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

            mailSender.send(message);
            log.info("Contraseña temporal enviada a {} (usuario nuevo)", user.getEmail());
        } catch (MessagingException | RuntimeException e) {
            log.error("No se pudo enviar la contraseña temporal a {}: {}", user.getEmail(), e.getMessage());
        }
    }

    /**
     * Envía el link para restablecer contraseña del flujo "olvidé mi contraseña" (ver
     * {@code AuthService#forgotPassword}). El link ya trae el token de un solo uso; vigente
     * 30 minutos.
     *
     * @param user usuario que solicitó el restablecimiento
     * @param resetLink URL completa del frontend con el token en el query string
     */
    /** Enlace para confirmar el correo de un cliente de la página web (válido 48 h). */
    @Async
    public void sendClienteVerificacionEmail(String toEmail, String nombre, String tienda, String link) {
        enviarTexto(toEmail, "Confirma tu correo — " + tienda,
                "Hola " + nombre + ",\n\n" +
                "Gracias por registrarte en " + tienda + ". Confirma tu correo para poder hacer pedidos con tu cuenta " +
                "(el enlace es válido por 48 horas):\n\n" + link + "\n\n" +
                "Si tú no creaste esta cuenta, ignora este correo.");
    }

    /** Enlace para que un cliente de la página web restablezca su contraseña (válido 1 h, un solo uso). */
    @Async
    public void sendClienteRestablecerEmail(String toEmail, String nombre, String tienda, String link) {
        enviarTexto(toEmail, "Restablece tu contraseña — " + tienda,
                "Hola " + nombre + ",\n\n" +
                "Recibimos una solicitud para restablecer tu contraseña en " + tienda + ". El enlace es válido por 1 hora " +
                "y solo se puede usar una vez:\n\n" + link + "\n\n" +
                "Si tú no lo pediste, ignora este correo: tu contraseña sigue siendo la misma.");
    }

    /** Aviso al cliente sobre su reservación de mesa (confirmada, rechazada, cancelada). */
    @Async
    public void sendReservacionEmail(String toEmail, String asunto, String texto) {
        enviarTexto(toEmail, asunto, texto);
    }

    private void enviarTexto(String toEmail, String asunto, String texto) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setTo(toEmail);
            helper.setSubject(asunto);
            helper.setText(texto);
            mailSender.send(message);
            log.info("Correo \"{}\" enviado a {}", asunto, toEmail);
        } catch (MessagingException | RuntimeException e) {
            log.error("No se pudo enviar el correo \"{}\" a {}: {}", asunto, toEmail, e.getMessage());
        }
    }

    @Async
    public void sendPasswordResetEmail(User user, String resetLink) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(user.getEmail());
            helper.setSubject("Recupera tu contraseña");
            helper.setText(
                    "Hola " + user.getName() + ",\n\n" +
                    "Recibimos una solicitud para restablecer tu contraseña. Este link es válido por 30 minutos:\n\n" +
                    resetLink + "\n\n" +
                    "Si tú no pediste esto, puedes ignorar este correo — tu contraseña sigue siendo la misma."
            );

            mailSender.send(message);
            log.info("Correo de recuperación de contraseña enviado a {}", user.getEmail());
        } catch (MessagingException | RuntimeException e) {
            log.error("No se pudo enviar el correo de recuperación de contraseña a {}: {}", user.getEmail(), e.getMessage());
        }
    }
}
