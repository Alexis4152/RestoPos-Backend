package com.nexora.restopos.model;

/**
 * Forma de pago con la que se liquidó una {@link Sale}.
 * <p>
 * Se usa también para clasificar los totales de un {@link CashCut} en
 * {@code cashSales}, {@code cardSales} y {@code transferSales}.
 */
public enum PaymentMethod {
    /** Pago en efectivo. */
    CASH,
    /** Pago con tarjeta (débito o crédito). */
    CARD,
    /** Pago por transferencia bancaria. */
    TRANSFER
}
