package com.nexora.restopos.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Payload para actualizar el horario global de cierre automático de cortes de caja
 * ({@code PUT /api/cash-cuts/schedule}). Es una configuración única en toda la plataforma
 * (una sola fila de {@code CashCutSchedule} en base de datos) y aplica por igual a todas
 * las tiendas; solo el SUPER_ADMIN puede modificarla.
 * <p>
 * Cuando llega la hora {@code closeHour}:{@code closeMinute} y {@code enabled} es
 * {@code true}, el job programado cierra automáticamente todos los cortes que sigan
 * abiertos (sin gastos capturados y con {@code closedBy} en null, indicando que el sistema
 * fue quien cerró, no una persona).
 */
@Data
public class CashCutScheduleRequest {
    @NotNull @Min(0) @Max(23)
    private Integer closeHour;
    @NotNull @Min(0) @Max(59)
    private Integer closeMinute;
    @NotNull
    private Boolean enabled;
}
