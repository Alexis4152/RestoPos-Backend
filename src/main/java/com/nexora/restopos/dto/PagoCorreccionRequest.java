package com.nexora.restopos.dto;

import com.nexora.restopos.model.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Corrección del método de un pago mal capturado ({@code POST /api/payments/{id}/metodo}). */
@Data
public class PagoCorreccionRequest {
    @NotNull(message = "Elige el método correcto")
    private PaymentMethod metodo;
    // Obligatoria si el método correcto es transferencia.
    @Size(max = 100, message = "La referencia no puede tener más de 100 caracteres")
    private String referencia;
    private String motivo;
}
