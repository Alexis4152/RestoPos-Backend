package com.nexora.restopos.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/** Devolución de dinero de un pago ({@code POST /api/payments/{id}/devolver}). */
@Data
public class PagoDevolucionRequest {
    @NotNull(message = "Captura el monto a devolver") @Positive(message = "El monto debe ser mayor a cero")
    @DecimalMax(value = "9999999999.99", message = "El número es excesivamente grande")
    private BigDecimal monto;
    private String motivo;
}
