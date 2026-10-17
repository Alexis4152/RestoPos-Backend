package com.nexora.restopos.dto;

import com.nexora.restopos.model.ArqueoResolution;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Payload de {@code POST /api/cash-cuts/{id}/resolve-count}: resolver la diferencia del
 * arqueo de cierre. Para {@code SHORTAGE}, {@code responsibleId} es quien cubre el faltante.
 */
@Data
public class ArqueoResolveRequest {
    @NotNull(message = "Indica qué pasó")
    private ArqueoResolution resolution;

    private Long responsibleId;

    @NotBlank(message = "La nota es obligatoria")
    @Size(max = 200, message = "La nota no puede tener más de 200 caracteres")
    private String note;
}
