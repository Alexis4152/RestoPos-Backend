package com.nexora.restopos.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Credenciales de acceso enviadas a {@code POST /api/auth/login}. Es el único DTO de
 * entrada del flujo de autenticación; si las credenciales son válidas, el servicio
 * responde con un {@link LoginResponse} que incluye el JWT.
 */
@Data
public class LoginRequest {
    @Email @NotBlank
    private String email;
    @NotBlank
    private String password;
}
