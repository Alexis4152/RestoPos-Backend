package com.nexora.restopos.model;

/**
 * Ciclo de vida de una {@link Orden}: ABIERTA -&gt; EN_PROCESO -&gt; LISTA -&gt;
 * PENDIENTE_PAGO -&gt; PAGADA, con CANCELADA como salida en cualquier punto antes de PAGADA.
 */
public enum OrdenEstado {
    ABIERTA,
    EN_PROCESO,
    LISTA,
    PENDIENTE_PAGO,
    PAGADA,
    CANCELADA
}
