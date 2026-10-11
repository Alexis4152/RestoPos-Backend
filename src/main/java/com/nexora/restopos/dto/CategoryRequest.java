package com.nexora.restopos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalTime;

/**
 * Payload para crear o actualizar una {@code Category} de productos
 * ({@code POST}/{@code PUT /api/categories/**}, restringido a ADMIN). La categoría creada
 * queda asociada automáticamente a la tienda del actor; se usa para clasificar productos
 * en el inventario y como filtro en el buscador de productos.
 */
@Data
public class CategoryRequest {
    // max=100: debe coincidir con Category.name (VARCHAR(100)) — sin este tope, un nombre más
    // largo pasa la validación pero truena al guardar con un error crudo de la base de datos.
    @NotBlank
    @Size(max = 100, message = "El nombre no puede tener más de 100 caracteres")
    private String name;

    // categories.description es TEXT (sin límite de columna) — este tope es de higiene de
    // la app, no de la base de datos.
    @Size(max = 200, message = "La descripción no puede tener más de 200 caracteres")
    private String description;

    // Horario opcional (ej. "Desayuno" 10:00-12:00) usado cuando availabilityMode=AUTO; los
    // dos deben venir juntos o ninguno (ver CategoryService#validateSchedule). El modo en sí
    // (AUTO/FORCE_VISIBLE/FORCE_HIDDEN) no se edita aquí — tiene su propio endpoint más ligero
    // (PUT /api/categories/{id}/availability-mode) para que un mesero pueda cambiarlo sin
    // tocar el resto del formulario ni necesitar el rol ADMIN.
    private LocalTime scheduleStart;
    private LocalTime scheduleEnd;
}
