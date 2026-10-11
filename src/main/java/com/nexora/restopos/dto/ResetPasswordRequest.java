package com.nexora.restopos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordRequest {
    @NotBlank
    private String token;

    // 72: BCrypt trunca en silencio cualquier byte después de ese punto — sin este tope, dos
    // contraseñas que solo difieran después del carácter 72 se validarían como iguales.
    @NotBlank
    @Size(min = 6, max = 72, message = "La contraseña debe tener entre 6 y 72 caracteres")
    private String newPassword;
}
