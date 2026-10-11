package com.nexora.restopos.model;

/**
 * Cómo se decide si una {@link Category} está disponible para pedirse en este momento —
 * usado por {@link Category#isAvailableNow()} para calcular el estado real que consumen
 * Órdenes/Para llevar al armar la lista de productos elegibles.
 */
public enum CategoryAvailabilityMode {
    /** Se rige por {@link Category#getScheduleStart()}/{@link Category#getScheduleEnd()}; sin horario configurado, siempre disponible. */
    AUTO,
    /** Disponible siempre, sin importar la hora ni el horario configurado (anula el automático). */
    FORCE_VISIBLE,
    /** Oculta siempre, sin importar la hora ni el horario configurado (anula el automático). */
    FORCE_HIDDEN
}
