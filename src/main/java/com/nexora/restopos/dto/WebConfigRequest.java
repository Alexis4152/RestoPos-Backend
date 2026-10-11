package com.nexora.restopos.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Configuración de la página web pública de una tienda
 * ({@code PUT /api/tiendas/{id}/web-config}, solo admin) — ver TiendaInfoService#updateWebConfig,
 * que valida las reglas cruzadas (slug único, al menos una forma de pago, etc.).
 */
@Data
public class WebConfigRequest {
    private Boolean webEnabled;
    @Size(max = 80, message = "El link no puede tener más de 80 caracteres")
    private String publicSlug;
    private Boolean webMostrarMesas;
    private Boolean webRecogerEnabled;
    private Boolean webDomicilioEnabled;
    private Boolean webPagoEfectivo;
    private Boolean webPagoTarjeta;
    private Boolean webPagoTransferencia;
    @Size(max = 100, message = "El banco no puede tener más de 100 caracteres")
    private String transferenciaBanco;
    @Size(max = 150, message = "El titular no puede tener más de 150 caracteres")
    private String transferenciaTitular;
    @Size(max = 30, message = "La CLABE / cuenta no puede tener más de 30 caracteres")
    private String transferenciaClabe;
    @Size(max = 1000, message = "El mensaje no puede tener más de 1000 caracteres")
    private String webMensaje;
}
