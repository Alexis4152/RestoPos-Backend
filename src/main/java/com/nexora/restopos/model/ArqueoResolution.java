package com.nexora.restopos.model;

/**
 * Causa con la que un administrador resuelve una diferencia del arqueo de cierre de un
 * {@link CashCut} (lo contado al cerrar contra el efectivo esperado). La diferencia nunca se
 * borra: queda registrada con su resolución.
 */
public enum ArqueoResolution {
    /** Faltó dinero de verdad: se genera un {@link Shortage} a cargo de una persona. */
    SHORTAGE,
    /** Sobró dinero de verdad: queda registrado (el dinero ya está en la caja). */
    SURPLUS,
    /** Se contó mal: el dinero sí cuadraba. Si la caja sigue cerrada, se corrige su entrega pendiente. */
    COUNT_ERROR
}
