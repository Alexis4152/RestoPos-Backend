package com.nexora.restopos.model;

/**
 * Estado de un {@link CashCut} (corte de caja).
 */
public enum CashCutStatus {
    /** El corte sigue abierto: el cajero/vendedor puede seguir registrando ventas contra él. */
    OPEN,
    /** El corte ya fue cerrado, ya sea manualmente o por {@code CashCutAutoCloseJob}. */
    CLOSED
}
