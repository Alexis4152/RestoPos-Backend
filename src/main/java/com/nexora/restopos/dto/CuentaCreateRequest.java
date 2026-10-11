package com.nexora.restopos.dto;

import com.nexora.restopos.model.CuentaTipo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CuentaCreateRequest {
    @NotNull
    private CuentaTipo tipo;
    // requerido si tipo = POR_COMENSAL
    private Long comensalId;
    // requerido si tipo = PERSONALIZADA
    @Valid
    private List<CuentaItemRequest> items;
}
