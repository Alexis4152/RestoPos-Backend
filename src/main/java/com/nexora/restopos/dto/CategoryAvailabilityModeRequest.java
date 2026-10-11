package com.nexora.restopos.dto;

import com.nexora.restopos.model.CategoryAvailabilityMode;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Payload del endpoint ligero {@code PUT /api/categories/{id}/availability-mode}: cambia
 * únicamente el modo de disponibilidad de una categoría (no su nombre/descripción/horario),
 * para que cualquiera con acceso a Menú u Órdenes —no solo ADMIN— pueda forzarla visible u
 * oculta al momento, sin pasar por el formulario completo de edición.
 */
@Data
public class CategoryAvailabilityModeRequest {
    @NotNull(message = "Selecciona un modo de disponibilidad")
    private CategoryAvailabilityMode mode;
}
