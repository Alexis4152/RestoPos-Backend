package com.nexora.restopos.model;

/** De dónde salió el dinero con que se pagó un {@link Reimbursement}. */
public enum ReimbursementPaymentSource {
    /** De la caja de un corte abierto: se registra como salida {@link CashMovementType#REIMBURSEMENT}. */
    CASH_CUT,
    /** Por fuera de la caja (transferencia o efectivo externo): no toca ningún corte. */
    EXTERNAL
}
