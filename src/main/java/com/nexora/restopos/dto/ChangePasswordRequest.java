package com.nexora.restopos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Payload para que un usuario YA AUTENTICADO cambie su propia contraseña
 * ({@code POST /api/auth/change-password}) — exige la contraseña actual como comprobante
 * de identidad. Es el flujo que usa la pantalla obligatoria de "cambia tu contraseña"
 * cuando un admin dio de alta al usuario (o le reseteó la contraseña) con una temporal
 * generada por el sistema (ver {@code User#getMustChangePassword()}), pero cualquier
 * usuario puede usarlo también para cambiarla por gusto propio.
 */
@Data
public class ChangePasswordRequest {
    @NotBlank
    private String currentPassword;

    // 72 como tope porque BCrypt trunca en silencio cualquier byte de más allá del 72
    // (mismo motivo que UserRequest.password).
    @NotBlank
    @Size(min = 6, max = 72, message = "La nueva contraseña debe tener entre 6 y 72 caracteres")
    private String newPassword;
}
