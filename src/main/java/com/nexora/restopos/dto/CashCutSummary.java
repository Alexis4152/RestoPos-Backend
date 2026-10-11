package com.nexora.restopos.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Resumen de totales de un corte de caja, devuelto por
 * {@code GET /api/cash-cuts/{id}/summary}. Se calcula en el momento a partir de las
 * ventas asociadas al {@code CashCut} (sin importar si sigue abierto o ya fue cerrado),
 * desglosando el total por método de pago y separando las ventas canceladas del resto.
 */
@Data
@AllArgsConstructor
public class CashCutSummary {
    // Fondo inicial con el que se abrió el corte.
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
    // Movimientos manuales de efectivo (MovimientoCaja) registrados durante el corte.
    private BigDecimal totalEntradas;
    private BigDecimal totalSalidas;
}
