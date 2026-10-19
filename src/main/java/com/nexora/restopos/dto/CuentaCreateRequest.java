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
    // Modo rápido: el mesero confirma que ya entregó todo, así que lo que se factura se marca
    // como ENTREGADO al generar la cuenta, sin importar en qué estado lo dejó cocina (no siempre
    // alcanza a marcarlo). Sin esto (modo detallado) se exige marcarlo antes. Ver
    // CuentaService#crearInternal.
    private Boolean marcarEntregados;
}
