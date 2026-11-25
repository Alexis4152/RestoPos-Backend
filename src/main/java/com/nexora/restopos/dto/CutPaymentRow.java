package com.nexora.restopos.dto;

import com.nexora.restopos.model.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Un cobro cancelado o una devolución de un corte, para la tabla "Movimientos del turno"
 * ({@code GET /api/cash-cuts/{id}/cancelled-payments} y {@code /refunds}). No es un movimiento
 * de caja: se muestra junto a ellos.
 *
 * @param amount  siempre positivo
 * @param at      cuándo se canceló / devolvió
 * @param byName  quién lo canceló / devolvió
 * @param motivo  motivo de la cancelación o de la devolución
 * @param byOther lo canceló alguien distinto al dueño del corte
 */
public record CutPaymentRow(Long id, Long cuentaId, String folio, Long ordenId, String mesa,
                            PaymentMethod metodo, BigDecimal amount, LocalDateTime paidAt, LocalDateTime at,
                            String byName, String motivo, boolean byOther) {
}
