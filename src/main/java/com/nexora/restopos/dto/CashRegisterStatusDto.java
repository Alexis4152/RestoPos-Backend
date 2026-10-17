package com.nexora.restopos.dto;

import com.nexora.restopos.model.CashCountMethod;
import com.nexora.restopos.model.CashMovement;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Estado de una caja para la pantalla de Cajas y para elegir caja al abrir corte
 * ({@code GET /api/cash-registers}). Si está en uso trae {@code openCut}; si está cerrada
 * trae {@code pending}: el desglose de lo que recibirá quien la abra (lo que dejó el último
 * corte, los movimientos con la caja cerrada y el monto final).
 */
public record CashRegisterStatusDto(Long id, String name, boolean isActive, OpenCut openCut, Pending pending,
                                    boolean dayClosed, LocalDateTime lastDayCloseAt) {

    public record OpenCut(Long id, Long userId, String userName, LocalDateTime openedAt, BigDecimal expectedCash) {}

    public record Pending(BigDecimal amount,
                          CashCountMethod countMethod,
                          String countedByName,
                          LocalDateTime countedAt,
                          Long lastCutId,
                          String lastUserName,
                          LocalDateTime lastClosedAt,
                          BigDecimal leftAmount,
                          CashCountMethod leftCountMethod,
                          String leftCountedByName,
                          List<CashMovement> movements) {}
}
