package com.nexora.restopos.dto;

import com.nexora.restopos.model.CashCountMethod;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Payload de {@code POST /api/cash-cuts/{id}/force-close}: un administrador cierra el
 * corte de otro (p. ej. el cajero se fue sin cerrar). {@code countMethod} dice cómo se
 * contó el efectivo: {@code ADMIN_IN_PERSON} (lo contó el admin), {@code REMOTE_REPORTED}
 * (el cajero lo reportó a distancia: {@code reportedById} obligatorio) o {@code NOT_COUNTED}.
 */
@Data
public class CashCutForceCloseRequest {
    @NotNull(message = "Indica cómo se contó el efectivo")
    private CashCountMethod countMethod;

    @PositiveOrZero
    @DecimalMax(value = "9999999999.99", message = "El número es excesivamente grande — el máximo permitido es 9,999,999,999.99")
    private BigDecimal countedAmount;

    private Long reportedById;

    @Size(max = 300)
    private String evidencePath;

    @NotBlank(message = "Escribe el motivo del cierre forzado")
    @Size(max = 200, message = "El motivo no puede tener más de 200 caracteres")
    private String reason;
}
