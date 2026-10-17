package com.nexora.restopos.model;

/**
 * Estado de la recepción de caja de un {@link CashCut}: compara lo que debía recibir el
 * cajero (lo que dejó el turno anterior, más/menos los movimientos con la caja cerrada)
 * contra lo que contó al abrir.
 */
public enum HandoverStatus {
    /** No hubo entrega previa (primer corte de la caja, o corte anterior a las cajas). */
    NONE,
    /** Lo contado coincide con lo que debía recibir. */
    OK,
    /** Lo contado no coincide; pendiente de que un administrador la resuelva. */
    DIFFERENCE,
    /** Hubo diferencia y un administrador ya la resolvió (ver {@link HandoverResolution}). */
    RESOLVED
}
