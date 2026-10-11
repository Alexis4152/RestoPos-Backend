package com.nexora.restopos.dto;

import com.nexora.restopos.model.CashMovementType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Payload para registrar un movimiento manual de efectivo ({@code POST /api/cash-cuts/movements}).
 * <p>
 * A propósito NO se valida aquí que {@code monto} sea positivo: para {@code ENTRADA}/
 * {@code SALIDA} sí debe serlo (una salida se registra con magnitud positiva; el signo
 * "negativo" lo aplica el cálculo del cierre, no el dato guardado), pero un {@code AJUSTE}
 * puede ser negativo (corrige una diferencia de caja hacia abajo) — ver
 * {@code MovimientoCajaService#registrar} para la validación de signo específica por tipo, y
 * {@code CashCutService#sumarMovimientos} para cómo un AJUSTE negativo se resta del efectivo
 * esperado.
 */
@Data
public class MovimientoCajaRequest {
    @NotNull
    private CashMovementType tipo;
    // Tope = NUMERIC(12,2) de MovimientoCaja.monto, en ambos sentidos.
    @NotNull
    @DecimalMin(value = "-9999999999.99")
    @DecimalMax(value = "9999999999.99")
    private BigDecimal monto;
    @NotBlank
    @Size(max = 255)
    private String motivo;
}
