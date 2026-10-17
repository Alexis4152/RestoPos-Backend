package com.nexora.restopos.model;

/**
 * Cómo se verificó el efectivo de una caja al cerrar un corte (o al moverlo con la caja
 * cerrada). Viaja con la entrega al siguiente turno: si quien recibe cuenta otra cantidad,
 * esto dice a quién le corresponde revisar la diferencia.
 */
public enum CashCountMethod {
    /** Lo contó el propio cajero al cerrar su corte. */
    CASHIER,
    /** Lo contó un administrador/supervisor en persona, frente a la caja. */
    ADMIN_IN_PERSON,
    /** El cajero lo contó y se lo reportó a distancia a un administrador (llamada, mensaje, foto). */
    REMOTE_REPORTED,
    /** Nadie lo contó (cierre automático o forzado sin conteo): se entrega el efectivo esperado. */
    NOT_COUNTED
}
