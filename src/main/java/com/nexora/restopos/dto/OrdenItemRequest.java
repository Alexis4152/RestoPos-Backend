package com.nexora.restopos.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class OrdenItemRequest {
    @NotNull
    private Long productoId;
    // null = compartido por toda la mesa
    private Long comensalId;
    @NotNull @Positive
    private BigDecimal cantidad;
    @PositiveOrZero
    private BigDecimal descuento;
    private String observaciones;
    // ids de ModificadorOpcion elegidas (de cualquier grupo del producto), opcional — ver
    // OrdenService#agregarItem, que valida ahí las reglas de único/obligatorio.
    private List<Long> modificadorOpcionIds;
}
