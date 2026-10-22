package com.nexora.restopos.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Lo que ve el cliente de su pedido web: folio, total y en qué va. {@code estado} es una
 * versión simplificada para el cliente (RECIBIDO, ACEPTADO, LISTO, ENTREGADO, RECHAZADO), no
 * el estado interno de la orden.
 */
public record PublicPedidoStatus(
        Long id,
        String estado,
        String tipoEntrega,
        String metodoPago,
        BigDecimal total,
        LocalDateTime creadoEn,
        String motivoRechazo,
        List<Linea> items,
        Integer resenaEstrellas,
        String resenaComentario,
        // Cuándo se le entregó (a domicilio: lo marca el repartidor; para recoger: al cobrarse).
        LocalDateTime entregadoEn,
        // Reporte de cómo llegó (null si no ha reportado) y hasta cuándo puede hacerlo (24 h).
        String reporteTipo,
        String reporteProductos,
        String reporteComentario,
        LocalDateTime reportarHasta,
        boolean puedeReportar,
        // Solo órdenes de mesa (tipoEntrega = "MESA"): número de mesa.
        String mesa,
        // Pago por transferencia a domicilio: costo de envío del pedido (para que el cliente
        // decida si lo incluye), si ya subió la foto del comprobante (la foto solo la ve el
        // personal) y si la transferencia lo incluye.
        BigDecimal costoEnvio,
        boolean comprobanteEnviado,
        Boolean transferenciaIncluyeEnvio,
        // Efectivo: con cuánto dijo que paga (para mostrarle su cambio). null = no lo indicó.
        BigDecimal pagaCon,
        // A domicilio: el repartidor lo soltó y nadie lo ha vuelto a tomar ("buscando otro repartidor").
        boolean buscandoRepartidor
) {
    public record Linea(Long id, String nombre, BigDecimal cantidad, BigDecimal subtotal, List<String> modificadores) {}
}
