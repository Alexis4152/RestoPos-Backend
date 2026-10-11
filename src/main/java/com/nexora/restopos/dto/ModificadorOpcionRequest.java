package com.nexora.restopos.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ModificadorOpcionRequest {
    // null = opción nueva; con valor = actualiza esa opción existente del grupo
    // (ver ModificadorGrupoService#update).
    private Long id;

    @NotBlank
    @Size(max = 100, message = "El nombre no puede tener más de 100 caracteres")
    private String nombre;

    @NotNull
    @DecimalMin(value = "0", message = "El precio extra no puede ser negativo")
    private BigDecimal precioExtra;
}
