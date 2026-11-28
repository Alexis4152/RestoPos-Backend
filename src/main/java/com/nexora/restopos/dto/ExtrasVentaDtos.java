package com.nexora.restopos.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Peticiones para los grupos de Extras (ModificadorGrupo.esExtras) desde la toma de orden:
 * cambiar el precio de un extra o agregar uno nuevo. Ambos se guardan en el catálogo del platillo.
 */
public final class ExtrasVentaDtos {

    private ExtrasVentaDtos() {}

    /** Precio nuevo de un extra. */
    public record Precio(
            @NotNull(message = "Indica el precio del extra")
            @DecimalMin(value = "0", message = "El precio no puede ser negativo")
            @DecimalMax(value = "9999", message = "El precio es demasiado alto")
            BigDecimal precioExtra) {}

    /** Extra nuevo para el grupo. */
    public record Nuevo(
            @NotBlank(message = "Escribe el nombre del extra")
            @Size(max = 100, message = "El nombre no puede tener más de 100 caracteres")
            String nombre,
            @NotNull(message = "Indica el precio del extra")
            @DecimalMin(value = "0", message = "El precio no puede ser negativo")
            @DecimalMax(value = "9999", message = "El precio es demasiado alto")
            BigDecimal precioExtra) {}
}
