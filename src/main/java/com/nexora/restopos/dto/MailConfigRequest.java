package com.nexora.restopos.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Payload para actualizar la configuración SMTP global ({@code PUT /api/admin/mail-config},
 * exclusivo de SUPER_ADMIN).
 * <p>
 * {@code smtpPassword} es de solo escritura: si viene vacío o null se conserva la contraseña
 * ya guardada (ver {@code MailConfigService}) — el formulario nunca necesita mostrar ni
 * reenviar la contraseña real para dejarla como está.
 */
@Data
public class MailConfigRequest {

    @NotNull(message = "Indica si el envío de correo está habilitado")
    private Boolean enabled;

    @NotBlank(message = "El host SMTP es obligatorio")
    @Size(max = 150, message = "El host no puede tener más de 150 caracteres")
    private String smtpHost;

    @NotNull(message = "El puerto SMTP es obligatorio")
    @Min(value = 1, message = "El puerto debe estar entre 1 y 65535")
    @Max(value = 65535, message = "El puerto debe estar entre 1 y 65535")
    private Integer smtpPort;

    @NotBlank(message = "El usuario/cuenta SMTP es obligatorio")
    @Size(max = 150, message = "La cuenta no puede tener más de 150 caracteres")
    private String smtpUsername;

    @Size(max = 150, message = "La contraseña no puede tener más de 150 caracteres")
    private String smtpPassword;
}
