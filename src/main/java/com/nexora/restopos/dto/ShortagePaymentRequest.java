package com.nexora.restopos.dto;

import com.nexora.restopos.model.ShortagePaymentMethod;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Payload de {@code POST /api/shortages/{id}/payments}: pago total o abono a un faltante.
 * Con {@code CASH_REGISTER} el efectivo entra a la caja del corte abierto {@code cashCutId}.
 */
@Data
public class ShortagePaymentRequest {
    @NotNull(message = "Captura el monto")
    @Positive(message = "El monto debe ser mayor a cero")
    @DecimalMax(value = "9999999999.99", message = "El número es excesivamente grande — el máximo permitido es 9,999,999,999.99")
    private BigDecimal amount;

    @NotNull(message = "Indica cómo se paga")
    private ShortagePaymentMethod method;

    private Long cashCutId;

    @Size(max = 100, message = "La referencia no puede tener más de 100 caracteres")
    private String reference;

    @Size(max = 300)
    private String receiptPath;
}
