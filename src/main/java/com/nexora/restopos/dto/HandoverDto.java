package com.nexora.restopos.dto;

import com.nexora.restopos.model.CashCountMethod;
import com.nexora.restopos.model.CashMovement;
import com.nexora.restopos.model.HandoverResolution;
import com.nexora.restopos.model.HandoverStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Una entrega de caja ("Últimas entregas de caja"): de quién a quién, cuánto debía recibir,
 * cuánto contó quien recibió y, si hubo diferencia, cómo se resolvió.
 * {@code gapMovements} son los movimientos que un admin hizo con la caja cerrada entre ambos turnos.
 */
public record HandoverDto(Long cutId,
                          String registerName,
                          Long fromCutId,
                          Long fromUserId,
                          String fromUserName,
                          LocalDateTime fromClosedAt,
                          String toUserName,
                          LocalDateTime toOpenedAt,
                          BigDecimal expected,
                          BigDecimal received,
                          BigDecimal difference,
                          HandoverStatus status,
                          CashCountMethod receivedCountMethod,
                          String receiveNote,
                          HandoverResolution resolution,
                          String resolvedByName,
                          LocalDateTime resolvedAt,
                          String resolutionNote,
                          List<CashMovement> gapMovements,
                          Long shortageId,
                          String shortagePersonName) {}
