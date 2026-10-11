package com.nexora.restopos.dto;

import com.nexora.restopos.model.Cuenta;
import com.nexora.restopos.model.CuentaEstado;
import com.nexora.restopos.model.Mesa;
import com.nexora.restopos.model.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Una fila del módulo de Ventas: resume TODAS las cuentas de una {@link com.nexora.restopos.model.Orden}
 * en un solo renglón (una orden puede haberse cobrado con una mezcla de cuentas Total, Por
 * comensal y Personalizada — ver {@link CuentaEstado}), en vez de mostrar una fila por cada
 * cuenta suelta como antes.
 * <p>
 * {@code estado} es una propiedad agregada (no la de ninguna cuenta individual): PAGADA solo
 * si todas las cuentas no canceladas de la orden ya se cobraron, CANCELADA si todas sus cuentas
 * están canceladas, y PENDIENTE en cualquier otro caso (incluida una mezcla de pagadas y
 * pendientes) — ver {@code CuentaService#calcularEstadoAgregado}.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class VentaResumen {
    private Long ordenId;
    private Mesa mesa;
    private User mesero;
    /** Fecha/hora de la actividad más reciente entre las cuentas de la orden. */
    private LocalDateTime fecha;
    /** Suma del total de las cuentas no canceladas — lo que la orden realmente representa en ventas. */
    private BigDecimal total;
    private CuentaEstado estado;
    /** Desglose completo: cada cuenta (tipo, folio, monto, estado) con sus propios pagos. */
    private List<Cuenta> cuentas;
}
