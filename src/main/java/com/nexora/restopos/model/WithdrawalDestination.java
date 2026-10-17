package com.nexora.restopos.model;

/** A dónde fue el efectivo de un retiro ({@link CashMovementType#WITHDRAWAL}). */
public enum WithdrawalDestination {
    /** Entregado al dueño. */
    OWNER,
    /** Depósito bancario. */
    BANK,
    /** Caja fuerte. */
    SAFE
}
