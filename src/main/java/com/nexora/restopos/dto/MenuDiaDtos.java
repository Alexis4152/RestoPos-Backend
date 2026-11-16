package com.nexora.restopos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** DTOs de los menús con nombre (ver MenuDia / MenuDiaController). */
public final class MenuDiaDtos {

    private MenuDiaDtos() {}

    /** Crear o editar: nombre y la lista completa de platillos (reemplaza la anterior). */
    public record Guardar(
            @NotBlank(message = "Ponle un nombre al menú") @Size(max = 100, message = "Máximo 100 caracteres") String nombre,
            List<Long> productoIds) {}

    public record Menu(Long id, String nombre, boolean hoy, List<Long> productoIds, LocalDateTime updatedAt) {}
}
