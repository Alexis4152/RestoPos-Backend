package com.nexora.restopos.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Seguimiento de un envío a domicilio en el punto de venta. {@code estado}: SIN_REPARTIDOR,
 * TOMADO, EN_CAMINO, ENTREGADO o CANCELADO. {@code eventos}: histórico de pasos del repartidor.
 */
public record EntregaSeguimiento(String estado, String repartidor, LocalDateTime tomadoAt,
                                 LocalDateTime enCaminoAt, LocalDateTime entregadoAt, List<Evento> eventos,
                                 Cobro cobro) {

    /**
     * Cobro del pedido: si ya se cobró completo, cuánto falta, cómo dijo el cliente que pagaría
     * ({@code metodoAcordado}, solo pedidos web), si ya subió el comprobante de transferencia y
     * cada pago registrado (quién lo cobró y cuándo).
     */
    public record Cobro(boolean cobrado, java.math.BigDecimal total, java.math.BigDecimal pagado,
                        java.math.BigDecimal pendiente, String metodoAcordado, boolean comprobanteSubido,
                        List<PagoInfo> pagos, Transferencia transferencia) {}

    /**
     * Pago por transferencia de un pedido web: si ya subió el comprobante (y cuándo), si la
     * transferencia incluye el envío, cuánto debió transferir, cuánto del envío se cobra en
     * efectivo y si el repartidor ya la confirmó (registró el pago).
     */
    public record Transferencia(boolean comprobanteSubido, LocalDateTime comprobanteAt, Boolean incluyeEnvio,
                                java.math.BigDecimal costoEnvio, java.math.BigDecimal montoTransferencia,
                                java.math.BigDecimal envioEnEfectivo, boolean confirmada) {}

    public record PagoInfo(String metodo, java.math.BigDecimal monto, String cobradoPor, LocalDateTime fecha) {}

    /** Un paso: tipo (TOMADO, SOLTADO, EN_CAMINO, ENTREGADO), repartidor, quién lo hizo si fue otra persona, detalle y hora. */
    public record Evento(String tipo, String repartidor, String porOtro, String detalle, LocalDateTime fecha) {}
}
