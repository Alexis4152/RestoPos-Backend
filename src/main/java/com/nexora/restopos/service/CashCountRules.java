package com.nexora.restopos.service;

import java.math.BigDecimal;

/**
 * Reglas contra errores de dedo al contar efectivo (cierre de corte y recepción de caja).
 * Los mismos valores están en el frontend (pages/cashcuts/common.jsx) para avisar en vivo;
 * aquí se hacen cumplir.
 * <ul>
 *   <li><b>Diferencia grande</b>: más de {@link #LARGE_MIN} o del {@link #LARGE_PERCENT}% de lo
 *   esperado (lo que sea mayor). Se acepta, pero solo si el usuario volvió a escribir el monto.</li>
 *   <li><b>Monto no razonable</b>: más de {@link #UNREASONABLE_FACTOR} veces lo esperado más
 *   {@link #UNREASONABLE_MARGIN}. Se rechaza siempre: no es un conteo, sobran dígitos.</li>
 * </ul>
 */
final class CashCountRules {

    static final BigDecimal LARGE_MIN = new BigDecimal("200");
    static final BigDecimal LARGE_PERCENT = new BigDecimal("10");
    static final BigDecimal UNREASONABLE_FACTOR = new BigDecimal("10");
    static final BigDecimal UNREASONABLE_MARGIN = new BigDecimal("10000");

    private CashCountRules() {}

    static boolean isLargeDifference(BigDecimal expected, BigDecimal counted) {
        BigDecimal base = expected.max(BigDecimal.ZERO);
        BigDecimal threshold = base.multiply(LARGE_PERCENT).divide(new BigDecimal("100")).max(LARGE_MIN);
        return counted.subtract(expected).abs().compareTo(threshold) > 0;
    }

    static boolean isUnreasonable(BigDecimal expected, BigDecimal counted) {
        BigDecimal limit = expected.max(BigDecimal.ZERO).multiply(UNREASONABLE_FACTOR).add(UNREASONABLE_MARGIN);
        return counted.compareTo(limit) > 0;
    }
}
