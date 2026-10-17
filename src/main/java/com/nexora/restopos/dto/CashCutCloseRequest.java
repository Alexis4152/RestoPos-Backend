package com.nexora.restopos.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Payload de {@code POST /api/cash-cuts/{id}/close}: cierre con arqueo. Los gastos ya no
 * se capturan aquí (son {@code CashMovement} registrados durante el turno); lo que se pide
 * es cuánto efectivo hay de verdad en la caja.
 */
@Data
public class CashCutCloseRequest {
    @NotNull(message = "Captura cuánto efectivo contaste")
    @PositiveOrZero
    @DecimalMax(value = "9999999999.99", message = "El número es excesivamente grande — el máximo permitido es 9,999,999,999.99")
    private BigDecimal countedAmount;

    // Obligatoria (lo valida el servicio) si lo contado no cuadra con lo esperado.
    @Size(max = 200, message = "Las notas no pueden tener más de 200 caracteres")
    private String notes;

    // Confirmación reforzada: true cuando el usuario volvió a escribir el monto porque la
    // diferencia contra lo esperado es grande (ver CashCountRules). Sin ella, el backend
    // rechaza una diferencia grande con LargeCashDifferenceException.
    private Boolean largeDifferenceConfirmed;
}
