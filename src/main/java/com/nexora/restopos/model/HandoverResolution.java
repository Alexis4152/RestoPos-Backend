package com.nexora.restopos.model;

/**
 * Causa con la que un administrador resuelve una diferencia de entrega de caja
 * ({@link HandoverStatus#DIFFERENCE}). La diferencia nunca se borra: queda registrada con
 * su resolución.
 */
public enum HandoverResolution {
    /** Fue un retiro que no se registró: se registra con su fecha real, marcado como "registrado después". */
    UNREGISTERED_WITHDRAWAL,
    /** El dinero no apareció: la diferencia queda como faltante de esa entrega. */
    SHORTAGE,
    /** Se volvió a contar y el dinero sí estaba. */
    COUNT_ERROR
}
