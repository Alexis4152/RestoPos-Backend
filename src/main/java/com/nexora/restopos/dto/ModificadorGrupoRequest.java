package com.nexora.restopos.dto;

import com.nexora.restopos.model.ModificadorGrupoTipo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ModificadorGrupoRequest {
    @NotBlank
    @Size(max = 100, message = "El nombre no puede tener más de 100 caracteres")
    private String nombre;

    @NotNull
    private ModificadorGrupoTipo tipo;

    @NotNull
    private Boolean obligatorio;

    /** Solo para MULTIPLE: máximo de opciones a elegir; null o vacío = sin límite. */
    @Min(value = 1, message = "El máximo debe ser al menos 1")
    @Max(value = 50, message = "El máximo no puede pasar de 50")
    private Integer maxSelecciones;

    /** Solo para MULTIPLE: la misma opción se puede elegir varias veces; null = no. */
    private Boolean permiteRepetir;

    @NotEmpty(message = "Agrega al menos una opción")
    @Valid
    private List<ModificadorOpcionRequest> opciones;
}
