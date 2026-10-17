package com.nexora.restopos.dto;

import com.nexora.restopos.model.CashMovementType;
import com.nexora.restopos.model.WithdrawalDestination;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Payload para registrar un movimiento de efectivo, dentro de un corte abierto
 * ({@code POST /api/cash-cuts/{id}/movements}) o con la caja cerrada
 * ({@code POST /api/cash-registers/{id}/movements}, solo retiro o entrada de cambio).
 * Qué campos son obligatorios depende de {@code type} (lo valida el servicio).
 */
@Data
public class CashMovementRequest {
    @NotNull(message = "Indica el tipo de movimiento")
    private CashMovementType type;

    // Para REIMBURSEMENT se ignora: siempre se paga el monto completo del reembolso.
    @Positive(message = "El monto debe ser mayor a cero")
    @DecimalMax(value = "9999999999.99", message = "El número es excesivamente grande — el máximo permitido es 9,999,999,999.99")
    private BigDecimal amount;

    @Size(max = 150, message = "El concepto no puede tener más de 150 caracteres")
    private String concept;

    /** Quién puso el dinero (aporte) o quién retiró (retiro). */
    private Long personId;

    private WithdrawalDestination destination;

    @Size(max = 100, message = "La referencia no puede tener más de 100 caracteres")
    private String reference;

    @Size(max = 300)
    private String receiptPath;

    /** Reembolso a pagar (solo {@code REIMBURSEMENT}). */
    private Long reimbursementId;

    /** Préstamo entre cajas (LOAN_OUT / LOAN_IN): la otra caja de la tienda. */
    private Long counterpartRegisterId;

    /** Con la caja cerrada: lo que el admin contó que queda después del movimiento (opcional). */
    @PositiveOrZero
    @DecimalMax(value = "9999999999.99", message = "El número es excesivamente grande — el máximo permitido es 9,999,999,999.99")
    private BigDecimal countedRemaining;
}
