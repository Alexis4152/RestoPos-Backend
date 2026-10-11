package com.nexora.restopos.model;

/**
 * Estado de preparación de una línea de {@link Orden}.
 * <p>
 * Flujo de entrega: cocina marca {@link #ENTREGADO_MESERO} al pasarle el producto al mesero, y
 * el mesero marca {@link #ENTREGADO} al servirlo — "entregado a mesa" si la orden tiene mesa,
 * "entregado para llevar" si no (se distingue por {@code Orden.mesa}, no por el estado).
 */
public enum OrdenDetalleEstado {
    PENDIENTE,
    PREPARANDO,
    LISTO,
    /** Cocina ya se lo entregó al mesero; falta que el mesero lo lleve a la mesa / al cliente. */
    ENTREGADO_MESERO,
    /** El mesero ya lo entregó a la mesa (o al cliente, si es para llevar). */
    ENTREGADO,
    CANCELADO
}
