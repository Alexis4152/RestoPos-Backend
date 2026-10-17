package com.nexora.restopos.model;

/**
 * Tipo de un {@link CashMovement} (movimiento de efectivo que no es una venta).
 */
public enum CashMovementType {
    /** Salida: gasto pagado con dinero de la caja (pago a proveedor, insumos...). Baja la utilidad. */
    EXPENSE(true),
    /** Salida: retiro de efectivo (al dueño, depósito, caja fuerte). NO es gasto: el dinero sigue siendo de la tienda. */
    WITHDRAWAL(true),
    /** Salida: pago de un {@link Reimbursement} pendiente a quien puso dinero de su bolsa. */
    REIMBURSEMENT(true),
    /** Entrada: aporte de una persona que la tienda le debe regresar (genera un {@link Reimbursement}). */
    CONTRIBUTION(false),
    /** Entrada: cambio o fondo extra que se agrega a la caja y no se reembolsa. */
    FLOAT(false),
    /** Entrada: pago o abono de un {@link Shortage} (faltante) que entra a la caja. */
    SHORTAGE_PAYMENT(false),
    /** Salida: préstamo de efectivo a otra caja de la tienda. NO es gasto (el dinero sigue en la tienda). */
    LOAN_OUT(true),
    /** Entrada: préstamo de efectivo recibido de otra caja de la tienda. No es ingreso. */
    LOAN_IN(false);

    private final boolean outflow;

    CashMovementType(boolean outflow) {
        this.outflow = outflow;
    }

    /** {@code true} si el movimiento saca efectivo de la caja; {@code false} si lo mete. */
    public boolean isOutflow() {
        return outflow;
    }
}
