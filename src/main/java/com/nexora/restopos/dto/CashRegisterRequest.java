package com.nexora.restopos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Alta/edición de una caja ({@code POST/PUT /api/cash-registers}). */
@Data
public class CashRegisterRequest {
    @NotBlank(message = "Escribe el nombre de la caja")
    @Size(max = 60, message = "El nombre no puede tener más de 60 caracteres")
    private String name;

    // Solo en la edición: null = no cambiar.
    private Boolean isActive;
}
