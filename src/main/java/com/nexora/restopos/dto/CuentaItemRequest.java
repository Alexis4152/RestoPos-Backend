package com.nexora.restopos.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/** Un renglón de una cuenta {@code PERSONALIZADA}: qué línea de la orden y cuánta cantidad de ella. */
@Data
public class CuentaItemRequest {
    @NotNull
    private Long ordenDetalleId;
    @NotNull @Positive
    private BigDecimal cantidad;
}
