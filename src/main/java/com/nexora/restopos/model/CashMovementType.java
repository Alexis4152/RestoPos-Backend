package com.nexora.restopos.model;

/**
 * Tipo de un movimiento manual de efectivo en caja ({@link MovimientoCaja}). Las ventas no se
 * itemizan aquí: ya quedan registradas una por una como {@link Pago}, y se agregan al corte.
 */
public enum CashMovementType {
    ENTRADA,
    SALIDA,
    AJUSTE
}
