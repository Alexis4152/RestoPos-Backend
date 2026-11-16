package com.nexora.restopos.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Transferencia por confirmar contra el banco ({@code GET /api/payments/por-verificar}). */
public record TransferenciaPorVerificar(Long pagoId, Long ordenId, String folio, BigDecimal monto, String referencia,
                                        String verificacion, String cliente, String cobro, boolean comprobante,
                                        LocalDateTime fecha) {
}
