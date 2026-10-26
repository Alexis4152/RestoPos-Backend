package com.nexora.restopos.dto;

import com.nexora.restopos.model.PaymentMethod;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PagoRequest {
    @NotNull
    private PaymentMethod metodo;
    // Tope = NUMERIC(12,2) de Pago.monto; sin este @DecimalMax, un monto fuera de rango
    // revienta en un error crudo de base de datos en vez de un 400 legible (mismo caso ya
    // resuelto en CashCutRequest).
    @NotNull @Positive
    @DecimalMax(value = "9999999999.99", message = "El número es excesivamente grande — el máximo permitido es 9,999,999,999.99")
    private BigDecimal monto;
    // solo relevante para CASH, para calcular el cambio en el ticket
    @DecimalMax(value = "9999999999.99", message = "El número es excesivamente grande — el máximo permitido es 9,999,999,999.99")
    private BigDecimal recibido;
    private String referencia;
    // opcional: si se manda, al completarse el pago de la cuenta se envía el ticket por correo.
    // Tope = cuentas.ticket_email_destino VARCHAR(150) (ver Cuenta.ticketEmailDestino); sin
    // este @Size, un correo demasiado largo revienta en un error crudo de base de datos.
    @Email
    @Size(max = 150, message = "El correo no puede tener más de 150 caracteres")
    private String emailTicket;

    // Propina opcional, en monto fijo o porcentaje del subtotal de la cuenta — mutuamente
    // excluyentes (el porcentaje tiene prioridad si llegaran ambos). Solo tiene efecto si la
    // cuenta todavía no tiene ningún pago registrado (ver PagoService#registrar): se decide
    // junto con el primer pago, en el modal de "Registrar pago", nunca después.
    @DecimalMin(value = "0", message = "El porcentaje de propina no puede ser negativo")
    @DecimalMax(value = "50", message = "La propina no puede pasar del 50%")
    private BigDecimal propinaPorcentaje;

    @DecimalMin(value = "0", message = "La propina no puede ser negativa")
    private BigDecimal propinaMonto;
}
