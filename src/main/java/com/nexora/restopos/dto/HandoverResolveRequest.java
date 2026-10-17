package com.nexora.restopos.dto;

import com.nexora.restopos.model.HandoverResolution;
import com.nexora.restopos.model.WithdrawalDestination;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Payload de {@code POST /api/cash-registers/handovers/{cutId}/resolve}. Para
 * {@code UNREGISTERED_WITHDRAWAL} se registra el retiro olvidado ({@code amount},
 * {@code occurredAt}, {@code personId}, {@code destination}).
 */
@Data
public class HandoverResolveRequest {
    @NotNull(message = "Indica qué pasó")
    private HandoverResolution resolution;

    @Positive(message = "El monto debe ser mayor a cero")
    @DecimalMax(value = "9999999999.99", message = "El número es excesivamente grande — el máximo permitido es 9,999,999,999.99")
    private BigDecimal amount;

    private LocalDateTime occurredAt;

    private Long personId;

    private WithdrawalDestination destination;

    /** Para {@code SHORTAGE} con faltante: quién lo debe cubrir (se le genera un {@code Shortage}). */
    private Long responsibleId;

    @NotBlank(message = "La nota es obligatoria")
    @Size(max = 200, message = "La nota no puede tener más de 200 caracteres")
    private String note;
}
