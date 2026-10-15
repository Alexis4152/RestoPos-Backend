package com.nexora.restopos.model;

/**
 * Estado de una {@link Reservacion}. Flujo normal: PENDIENTE → CONFIRMADA → LLEGO. Desde
 * PENDIENTE el personal puede RECHAZAR; el cliente (o el personal) puede CANCELAR mientras no
 * haya pasado la hora; y una CONFIRMADA a la que nadie llegó se marca NO_LLEGO.
 */
public enum ReservacionEstado {
    PENDIENTE,
    CONFIRMADA,
    RECHAZADA,
    CANCELADA,
    LLEGO,
    NO_LLEGO
}
