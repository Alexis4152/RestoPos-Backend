package com.nexora.restopos.model;

/** De dónde salió un {@link Shortage}. */
public enum ShortageOrigin {
    /** Diferencia al recibir la caja (entrega entre turnos). */
    HANDOVER,
    /** Diferencia en el arqueo al cerrar el corte. */
    CLOSE
}
