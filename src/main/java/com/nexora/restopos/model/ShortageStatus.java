package com.nexora.restopos.model;

/** Estado de un {@link Shortage} según lo que la persona ya pagó. */
public enum ShortageStatus {
    /** No ha pagado nada. */
    PENDING,
    /** Ya abonó una parte. */
    PARTIAL,
    /** Pagó el total. */
    PAID
}
