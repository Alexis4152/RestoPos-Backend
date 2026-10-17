package com.nexora.restopos.exception;

/**
 * El efectivo contado (al cerrar un corte o al recibir una caja) difiere mucho de lo
 * esperado y el usuario no confirmó volviendo a escribir el monto. Es casi siempre un error
 * de dedo; {@link GlobalExceptionHandler} la responde con {@code data.largeDifference} para
 * que el frontend pida la confirmación reforzada.
 */
public class LargeCashDifferenceException extends IllegalStateException {
    public LargeCashDifferenceException(String message) {
        super(message);
    }
}
