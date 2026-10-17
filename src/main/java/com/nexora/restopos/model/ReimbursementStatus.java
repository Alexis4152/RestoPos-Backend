package com.nexora.restopos.model;

/** Estado de un {@link Reimbursement}. */
public enum ReimbursementStatus {
    /** La tienda todavía le debe el dinero a la persona. */
    PENDING,
    /** Ya se le pagó (desde una caja o por fuera de ella). */
    PAID
}
