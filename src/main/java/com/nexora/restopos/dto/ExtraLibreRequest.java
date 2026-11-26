package com.nexora.restopos.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Extra libre de una línea de orden: algo que no está en el menú (ej. "Aguacate" $12), con el
 * nombre y el precio que escribe quien toma la orden. Solo si la tienda lo tiene activado
 * (Tienda.extraLibre). Se guarda como modificador de la línea, marcado {@code libre}.
 */
@Data
public class ExtraLibreRequest {
    @NotBlank(message = "Escribe qué es el extra")
    @Size(max = 60, message = "El nombre del extra no puede tener más de 60 caracteres")
    private String nombre;

    @NotNull(message = "Indica el precio del extra")
    @DecimalMin(value = "0", message = "El precio del extra no puede ser negativo")
    @DecimalMax(value = "9999", message = "El precio del extra es demasiado alto")
    private BigDecimal precio;

    @NotNull
    @Min(value = 1, message = "La cantidad del extra debe ser al menos 1")
    @Max(value = 20, message = "La cantidad del extra no puede pasar de 20")
    private Integer cantidad;
}
