package com.nexora.restopos.exception;

/**
 * Lanzada por {@code AuthService#refreshAccessToken} cuando el refresh token recibido (vía
 * cookie httpOnly) no existe, está revocado o ya venció. Mapeada a 401 por
 * {@link GlobalExceptionHandler} — el mismo status que dispara el interceptor de axios del
 * frontend para intentar un refresh, así que aquí significa "ni refrescando se puede, hay
 * que volver a iniciar sesión".
 */
public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
