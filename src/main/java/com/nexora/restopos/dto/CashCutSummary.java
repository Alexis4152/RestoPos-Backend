package com.nexora.restopos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Resumen de totales de un corte de caja, devuelto por
 * {@code GET /api/cash-cuts/{id}/summary}. Se calcula en el momento a partir de las
 * ventas y los movimientos de efectivo asociados al {@code CashCut} (sin importar si sigue
 * abierto o ya fue cerrado), desglosando las ventas por método de pago y separando las
 * canceladas del resto.
 *
 * <p>{@code expectedCash} = fondo inicial + ventas en efectivo + entradas − gastos − retiros
 * − reembolsos pagados: el efectivo que debe haber en la caja ahora mismo (y, mientras el
 * corte sigue abierto, el máximo que se puede sacar con una salida).</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashCutSummary {
    // Fondo inicial con el que se abrió el corte (lo que contó el cajero al recibir).
    private BigDecimal openingAmount;
    private BigDecimal cashSales;
    private BigDecimal cardSales;
    private BigDecimal transferSales;
    // Suma de cashSales + cardSales + transferSales (ventas no canceladas).
    private BigDecimal totalSales;
    private int totalTransactions;
    // Ventas canceladas dentro de este corte: se cuentan aparte y no forman parte de totalSales.
    private int cancelledCount;
    private BigDecimal cancelledTotal;

    // Movimientos de efectivo del corte.
    private BigDecimal entriesTotal;
    private BigDecimal expensesTotal;
    private BigDecimal withdrawalsTotal;
    private BigDecimal reimbursementsTotal;
    // Préstamos a otras cajas: restan del efectivo pero no son gasto (no bajan la utilidad).
    private BigDecimal loansOutTotal;
    private BigDecimal expectedCash;
}
