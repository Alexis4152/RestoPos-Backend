package com.nexora.restopos.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Payload de {@code POST /api/cash-cuts/open}: abrir un corte recibiendo una caja.
 * El fondo inicial no se escribe libremente: es lo que el cajero CONTÓ al recibir la caja
 * ({@code countedAmount}); el backend lo compara contra lo que debía recibir.
 */
@Data
public class CashCutOpenRequest {
    @NotNull(message = "Elige una caja")
    private Long cashRegisterId;

    // Máximo alineado a cash_cuts.opening_amount NUMERIC(12,2).
    @NotNull(message = "Captura cuánto efectivo contaste")
    @PositiveOrZero
    @DecimalMax(value = "9999999999.99", message = "El número es excesivamente grande — el máximo permitido es 9,999,999,999.99")
    private BigDecimal countedAmount;

    // Obligatoria (lo valida el servicio) solo si lo contado no coincide con lo que debía recibir.
    @Size(max = 200, message = "La nota no puede tener más de 200 caracteres")
    private String note;

    // El "Conté el efectivo y estoy de acuerdo" — sin él no se abre el corte.
    private Boolean agreed;

    // Confirmación reforzada: true cuando el usuario volvió a escribir el monto porque la
    // diferencia contra lo esperado es grande (ver CashCountRules). Sin ella, el backend
    // rechaza una diferencia grande con LargeCashDifferenceException.
    private Boolean largeDifferenceConfirmed;
}
