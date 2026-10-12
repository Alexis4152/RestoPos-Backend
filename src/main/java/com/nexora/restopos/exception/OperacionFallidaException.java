package com.nexora.restopos.exception;

/**
 * Falla interna "esperada" cuyo mensaje SÍ es seguro mostrarle al usuario (ej. "No se pudo
 * guardar la imagen"): se responde 500 con ese mensaje, a diferencia de cualquier otra
 * excepción no controlada, cuyo mensaje crudo nunca sale al cliente (ver GlobalExceptionHandler).
 * La causa original se conserva para el log.
 */
public class OperacionFallidaException extends RuntimeException {
    public OperacionFallidaException(String mensajeParaUsuario, Throwable causa) {
        super(mensajeParaUsuario, causa);
    }
}
