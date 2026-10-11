package com.nexora.restopos.dto;

import com.nexora.restopos.model.ModificadorGrupoTipo;
import jakarta.validation.Valid;
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

    @NotEmpty(message = "Agrega al menos una opción")
    @Valid
    private List<ModificadorOpcionRequest> opciones;
}
