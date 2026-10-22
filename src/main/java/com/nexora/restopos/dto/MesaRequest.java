package com.nexora.restopos.dto;

import com.nexora.restopos.model.MesaEstado;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MesaRequest {
    @NotBlank
    private String numero;
    private Integer capacidad;
    // toda mesa pertenece a una zona (ver ZonaRequest) — sin límite de zonas ni de mesas
    // por zona, pero la mesa siempre debe elegir una.
    @NotNull
    private Long zonaId;
    // solo se respeta si la mesa no tiene una orden activa (ver MesaService.update)
    private MesaEstado estado;
    // Disponible para reservaciones (null = no se cambia; al crear, sí)
    private Boolean reservable;
}
