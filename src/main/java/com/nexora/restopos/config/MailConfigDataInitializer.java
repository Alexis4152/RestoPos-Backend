package com.nexora.restopos.config;

import com.nexora.restopos.model.MailConfig;
import com.nexora.restopos.repository.MailConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Siembra la fila única de {@link MailConfig} la primera vez que arranca la aplicación con
 * la tabla {@code mail_config} vacía. Después de eso no vuelve a tocarla: la cuenta se
 * administra desde "Correo del sistema" ({@code /api/admin/mail-config}).
 *
 * <p>Para no cortar el envío de correo al desplegar este cambio, toma como punto de partida
 * la cuenta que hasta ahora venía de las variables de entorno {@code APP_MAIL_USERNAME}/
 * {@code APP_MAIL_PASSWORD} (vía {@code spring.mail.*}): si ambas traen valor, la fila nace
 * habilitada con esa cuenta (la contraseña se guarda cifrada, ver {@code
 * EncryptedStringConverter}); si no, nace deshabilitada y sin credenciales — el envío se
 * omite sin bloquear el resto de la app hasta que un SUPER_ADMIN capture la cuenta.</p>
 */
@Component
@Order(40)
@RequiredArgsConstructor
@Slf4j
public class MailConfigDataInitializer implements CommandLineRunner {

    private final MailConfigRepository mailConfigRepository;

    @Value("${spring.mail.host:smtp.gmail.com}")
    private String host;

    @Value("${spring.mail.port:587}")
    private Integer port;

    @Value("${spring.mail.username:}")
    private String username;

    @Value("${spring.mail.password:}")
    private String password;

    @Override
    public void run(String... args) {
        if (mailConfigRepository.count() > 0) return;
        boolean hasAccount = username != null && !username.isBlank() && password != null && !password.isBlank();
        mailConfigRepository.save(MailConfig.builder()
                .enabled(hasAccount)
                .smtpHost(host == null || host.isBlank() ? "smtp.gmail.com" : host)
                .smtpPort(port == null ? 587 : port)
                .smtpUsername(hasAccount ? username : null)
                .smtpPassword(hasAccount ? password : null)
                .build());
        if (hasAccount) {
            log.info("Configuración de correo sembrada en mail_config con la cuenta de las variables de entorno ({})", username);
        } else {
            log.info("Configuración de correo sembrada en mail_config (deshabilitada, sin credenciales — configúrala en Correo del sistema)");
        }
    }
}
