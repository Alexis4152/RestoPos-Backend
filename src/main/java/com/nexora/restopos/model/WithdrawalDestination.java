package com.nexora.restopos.model;

/** A dónde fue el efectivo de un retiro ({@link CashMovementType#WITHDRAWAL}). */
public enum WithdrawalDestination {
    /** Entregado al dueño. */
    OWNER("Entregado al dueño"),
    /** Depósito bancario. */
    BANK("Depósito bancario"),
    /** Caja fuerte. */
    SAFE("Caja fuerte"),
    /** Otro: quien retira lo escribe a mano ({@code CashMovement#destinationOther}). */
    OTHER("Otro");

    private final String label;

    WithdrawalDestination(String label) {
        this.label = label;
    }

    /** El destino para mostrarlo; en {@link #OTHER}, lo que se escribió a mano. */
    public String describe(String other) {
        return this == OTHER && other != null ? other : label;
    }
}
