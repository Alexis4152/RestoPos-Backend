package com.nexora.restopos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Payload para crear o actualizar una {@code Zona} ({@code POST}/{@code PUT /api/zonas/**},
 * restringido a ADMIN). La zona creada queda asociada automáticamente a la tienda del actor.
 */
@Data
public class ZonaRequest {
    // max=100: debe coincidir con Zona.nombre (VARCHAR(100)) — sin este tope, un nombre más
    // largo pasa la validación pero truena al guardar con un error crudo de la base de datos.
    @NotBlank
    @Size(max = 100, message = "El nombre no puede tener más de 100 caracteres")
    private String nombre;
}
