package com.nexora.restopos.exception;

/** Sesión de cliente de la página web ausente, inválida o vencida — se responde 401. */
public class ClienteNoAutenticadoException extends RuntimeException {
    public ClienteNoAutenticadoException(String message) {
        super(message);
    }
}
