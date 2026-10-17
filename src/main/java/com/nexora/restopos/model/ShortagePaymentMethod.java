package com.nexora.restopos.model;

/** Cómo se cubrió un pago/abono de un {@link Shortage}. */
public enum ShortagePaymentMethod {
    /** Efectivo que entra a la caja de un corte abierto (queda como entrada {@link CashMovementType#SHORTAGE_PAYMENT}). */
    CASH_REGISTER,
    /** Transferencia a la tienda/dueño. */
    TRANSFER,
    /** Efectivo entregado directamente al administrador, fuera de cualquier caja. */
    EXTERNAL_CASH,
    /** Descuento de nómina. */
    PAYROLL
}
