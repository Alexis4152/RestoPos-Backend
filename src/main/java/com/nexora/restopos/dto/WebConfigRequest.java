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

    // Reservaciones de mesa
    private Boolean webReservasEnabled;
    @jakarta.validation.constraints.Min(value = 1, message = "El máximo de personas debe ser al menos 1")
    @jakarta.validation.constraints.Max(value = 100, message = "El máximo de personas no puede pasar de 100")
    private Integer reservaMaxPersonas;
    @jakarta.validation.constraints.Min(value = 0, message = "La anticipación no puede ser negativa")
    @jakarta.validation.constraints.Max(value = 168, message = "La anticipación no puede pasar de 168 horas (7 días)")
    private Integer reservaAnticipacionHoras;
    @jakarta.validation.constraints.Min(value = 1, message = "Permite reservar al menos 1 día adelante")
    @jakarta.validation.constraints.Max(value = 365, message = "No se puede reservar con más de 365 días")
    private Integer reservaDiasMax;
    private java.time.LocalTime reservaHoraInicio;
    private java.time.LocalTime reservaHoraFin;
    private Boolean reservaTodasMesas;
    @jakarta.validation.constraints.Min(value = 0, message = "Las horas para hacer cambios no pueden ser negativas")
    @jakarta.validation.constraints.Max(value = 168, message = "Las horas para hacer cambios no pueden pasar de 168")
    private Integer reservaCambiosHoras;
    private Boolean reservaRetrasoEnabled;
    @jakarta.validation.constraints.Min(value = 0, message = "Las horas para avisar retraso no pueden ser negativas")
    @jakarta.validation.constraints.Max(value = 72, message = "Las horas para avisar retraso no pueden pasar de 72")
    private Integer reservaRetrasoHoras;
}
