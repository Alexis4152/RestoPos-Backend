package com.nexora.restopos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Respuesta devuelta por {@code POST /api/auth/refresh}: solo el access token JWT nuevo. El
 * frontend ya tiene los demás datos de sesión (usuario, rol, tienda) cacheados en memoria/
 * localStorage desde el login — refrescar el token no necesita repetirlos.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RefreshResponse {
    private String token;
}
