package com.nexora.restopos.model;

/**
 * Tipo de movimiento de inventario registrado en {@link InventoryMovement}.
 */
public enum MovementType {
    /** Entrada de mercancía (ej. compra a proveedor). */
    IN,
    /** Salida de mercancía no asociada a una venta (ej. merma, devolución a proveedor). */
    OUT,
    /** Corrección manual del stock (ej. tras un conteo físico de inventario). */
    ADJUSTMENT,
    /** Salida de stock generada automáticamente al completarse una venta. */
    SALE
}
