package com.nexora.restopos.dto;

import com.nexora.restopos.model.PaymentMethod;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * El repartidor entrega un pedido a domicilio ({@code POST /api/deliveries/{id}/entregar}) y,
 * si todavía no está pagado, lo cobra: en efectivo ({@code recibido} para calcular el cambio)
 * o por transferencia a la cuenta de la tienda ({@code referencia} opcional).
 * {@code metodo} se ignora si el pedido ya estaba pagado.
 */
@Data
public class EntregaRequest {
    // CASH o TRANSFER (tarjeta no: el repartidor no lleva terminal). Requerido si hay saldo.
    private PaymentMethod metodo;
    @DecimalMax(value = "9999999999.99", message = "El número es excesivamente grande")
    private BigDecimal recibido;
    @Size(max = 100, message = "La referencia no puede tener más de 100 caracteres")
    private String referencia;
}
