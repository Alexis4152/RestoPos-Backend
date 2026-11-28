package com.nexora.restopos.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Edición de una línea ya agregada a la orden (ver OrdenService#editarItem). El producto no se
 * cambia aquí: para eso se cancela la línea y se agrega otra.
 */
@Data
public class OrdenItemUpdateRequest {
    // null = compartido por toda la mesa
    private Long comensalId;
    @NotNull @Positive
    private BigDecimal cantidad;
    private String observaciones;
    // null = conservar los modificadores actuales; lista (aunque sea vacía) = reemplazarlos.
    private List<Long> modificadorOpcionIds;
    // Opcional: por qué se editó (ej. "el cliente regresó uno") — solo va a la auditoría.
    private String motivo;
}
