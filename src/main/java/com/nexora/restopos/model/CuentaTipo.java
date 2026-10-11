package com.nexora.restopos.model;

/** Forma en que se agrupan los productos de una {@link Orden} para cobrarse en una {@link Cuenta}. */
public enum CuentaTipo {
    /** Todos los productos pendientes de pago de la orden. */
    TOTAL,
    /** Solo los productos de un {@link Comensal} en particular. */
    POR_COMENSAL,
    /** Selección manual de productos/unidades, sin importar el comensal. */
    PERSONALIZADA
}
