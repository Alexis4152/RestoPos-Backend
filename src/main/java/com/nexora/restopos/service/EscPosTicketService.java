package com.nexora.restopos.service;

import com.nexora.restopos.model.Cuenta;
import com.nexora.restopos.model.CuentaDetalle;
import com.nexora.restopos.model.CuentaEstado;
import com.nexora.restopos.model.Orden;
import com.nexora.restopos.model.OrdenDetalleModificador;
import com.nexora.restopos.model.Pago;
import com.nexora.restopos.model.PaymentMethod;
import com.nexora.restopos.model.Tienda;
import com.nexora.restopos.model.TiendaInfo;
import com.nexora.restopos.repository.TiendaInfoRepository;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Genera el ticket de una {@link Cuenta} en formato ESC/POS: la secuencia de bytes (texto +
 * comandos de control) que entienden directamente las impresoras térmicas de recibos, a
 * diferencia de {@link TicketPdfService}, que genera un PDF pensado para adjuntarse a un
 * correo. Puerto directo del mismo servicio de DemoPV, adaptado al modelo de Cuenta/Orden
 * de RestoPOS (una cuenta puede tener varios {@link Pago} — pago mixto — a diferencia de la
 * venta de pago único de DemoPV).
 *
 * <p>Este ticket no lo imprime el backend: el navegador del cajero, con ayuda de un puente
 * local instalado en su propia computadora (QZ Tray, que sí puede hablarle a un puerto USB),
 * es quien manda estos bytes tal cual a la impresora física. El backend solo construye el
 * contenido, nunca toca hardware directamente.</p>
 *
 * <p>El resultado se entrega como una cadena hexadecimal (dos caracteres ASCII por byte), no
 * como texto con caracteres de control incrustados: los comandos ESC/POS incluyen valores de
 * byte que no son texto válido (ej. 0xFA para el temporizador del cajón), y viajar como texto
 * normal por JSON/HTTP arriesgaría que una conversión de codificación los corrompiera antes de
 * llegar a la impresora.</p>
 *
 * <p>Pensado para papel térmico de 58mm (32 caracteres por línea a fuente estándar).</p>
 */
@Service
@RequiredArgsConstructor
public class EscPosTicketService {

    private static final int WIDTH = 32;

    private static final NumberFormat MONEY_FMT = NumberFormat.getCurrencyInstance(new Locale("es", "MX"));
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final TiendaInfoRepository tiendaInfoRepository;
    private final OpinionService opinionService;

    /**
     * Arma el ticket completo de una cuenta y lo entrega como cadena hexadecimal, lista para
     * que el frontend se la pase tal cual a QZ Tray como impresión "raw" en formato hex.
     *
     * <p>La apertura del cajón se agrega al final SOLO si la cuenta tiene al menos un pago en
     * efectivo no cancelado — no tiene sentido abrir la caja si se pagó solo con tarjeta o
     * transferencia, donde no hay dinero físico que entregar de cambio.</p>
     *
     * @param cuenta cuenta ya cobrada (con sus {@link CuentaDetalle} y {@link Pago} cargados)
     * @return el ticket completo codificado en hexadecimal
     */
    public String build(Cuenta cuenta) {
        return build(cuenta, 1);
    }

    /**
     * Igual que {@link #build(Cuenta)} pero con varias copias en un solo trabajo de impresión,
     * cada una con su leyenda: con 2, la primera es "COPIA CLIENTE" (la que se lleva el cliente
     * a la caja, con el QR de opinión) y la segunda "COPIA COMERCIO". Si la cuenta todavía no
     * está pagada, cada copia dice "CUENTA POR PAGAR" para que nadie la tome como comprobante.
     *
     * @param copias de 1 a 3 (fuera de rango se ajusta)
     */
    public String build(Cuenta cuenta, int copias) {
        int n = Math.max(1, Math.min(copias, 3));
        boolean porPagar = cuenta.getEstado() == CuentaEstado.PENDIENTE;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        boolean huboEfectivo = false;
        for (int i = 0; i < n; i++) {
            String etiqueta = n == 1 ? null : i == 0 ? "COPIA CLIENTE" : i == 1 ? "COPIA COMERCIO" : "COPIA " + (i + 1);
            huboEfectivo = copia(buf, cuenta, etiqueta, porPagar, i == 0);
        }
        // Solo se abre el cajon si hubo al menos un pago en efectivo (ver Javadoc de build()).
        // ESC p m t1 t2 : pulso al pin 2 del conector RJ11 del cajon (m=0).
        if (huboEfectivo) {
            ctl(buf, 0x1B, 0x70, 0x00, 0x19, 0xFA);
        }
        return toHex(buf.toByteArray());
    }

    /** Escribe una copia completa del ticket (con su corte de papel); devuelve si hubo pago en efectivo. */
    private boolean copia(ByteArrayOutputStream buf, Cuenta cuenta, String etiqueta, boolean porPagar, boolean conQr) {
        Orden orden = cuenta.getOrden();
        Tienda tienda = orden.getTienda();
        TiendaInfo info = tienda != null ? tiendaInfoRepository.findByTiendaId(tienda.getId()).orElse(null) : null;

        ctl(buf, 0x1B, 0x40); // ESC @ : inicializa la impresora

        ctl(buf, 0x1B, 0x61, 0x01); // alinear al centro
        ctl(buf, 0x1B, 0x45, 0x01); // negritas ON
        text(buf, wrap(tienda != null ? tienda.getName() : "RestoPOS") + "\n");
        ctl(buf, 0x1B, 0x45, 0x00); // negritas OFF

        if (info != null && info.getRfc() != null && !info.getRfc().isBlank()) {
            text(buf, "RFC: " + info.getRfc() + "\n");
        }
        if (porPagar || etiqueta != null) {
            ctl(buf, 0x1B, 0x45, 0x01);
            if (porPagar) text(buf, "CUENTA POR PAGAR\n");
            if (etiqueta != null) text(buf, etiqueta + "\n");
            ctl(buf, 0x1B, 0x45, 0x00);
            if (porPagar) text(buf, "No es comprobante de pago\n");
        }
        ctl(buf, 0x1B, 0x61, 0x00); // alinear a la izquierda
        text(buf, line());

        String folio = cuenta.getTicketFolio() != null ? cuenta.getTicketFolio() : String.valueOf(cuenta.getId());
        text(buf, "Folio: " + folio + "\n");
        if (cuenta.getCreatedAt() != null) {
            text(buf, DATE_FMT.format(cuenta.getCreatedAt()) + "\n");
        }
        if (orden.getMesero() != null) {
            text(buf, "Atendio: " + wrap(orden.getMesero().getName()) + "\n");
        }
        text(buf, (orden.getMesa() != null ? "Mesa: " + orden.getMesa().getNumero()
                : OrdenService.etiquetaTipoEntrega(orden.getTipoEntrega())) + "\n");
        if (cuenta.getComensal() != null) {
            String nombre = cuenta.getComensal().getNombre();
            text(buf, "Comensal " + cuenta.getComensal().getNumero() + (nombre != null && !nombre.isBlank() ? " (" + wrap(nombre) + ")" : "") + "\n");
        }
        text(buf, line());

        for (CuentaDetalle item : cuenta.getItems()) {
            text(buf, wrap(item.getOrdenDetalle().getProductoNombre()) + "\n");
            BigDecimal unitario = item.getCantidad().compareTo(BigDecimal.ZERO) > 0
                    ? item.getMonto().divide(item.getCantidad(), 2, RoundingMode.HALF_UP)
                    : item.getMonto();
            String left = stripTrailingZeros(item.getCantidad()) + " x " + money(unitario);
            text(buf, padBetween(left, money(item.getMonto())) + "\n");

            for (OrdenDetalleModificador mod : item.getOrdenDetalle().getModificadores()) {
                boolean tieneCosto = mod.getPrecioExtra().compareTo(BigDecimal.ZERO) > 0;
                text(buf, padBetween("  " + wrap(mod.getGrupoNombre() + " " + mod.getNombre()), tieneCosto ? money(mod.getPrecioExtra()) : "") + "\n");
            }
        }
        text(buf, line());

        if (cuenta.getPropina() != null && cuenta.getPropina().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal subtotal = cuenta.getTotal().subtract(cuenta.getPropina());
            String etiquetaPropina = cuenta.getPropinaPorcentaje() != null
                    ? "Propina (" + cuenta.getPropinaPorcentaje().stripTrailingZeros().toPlainString() + "%)"
                    : "Propina";
            text(buf, padBetween("Subtotal", money(subtotal)) + "\n");
            text(buf, padBetween(etiquetaPropina, money(cuenta.getPropina())) + "\n");
        }
        ctl(buf, 0x1B, 0x45, 0x01);
        text(buf, padBetween("TOTAL", money(cuenta.getTotal())) + "\n");
        ctl(buf, 0x1B, 0x45, 0x00);

        boolean huboEfectivo = false;
        BigDecimal recibidoTotal = BigDecimal.ZERO;
        BigDecimal cambioTotal = BigDecimal.ZERO;
        int pagosActivos = 0;
        for (Pago pago : cuenta.getPagos()) {
            if (Boolean.TRUE.equals(pago.getCancelado())) continue;
            pagosActivos++;
            text(buf, padBetween(metodoPagoEs(pago.getMetodo()), money(pago.getMonto())) + "\n");
            if (pago.getMetodo() == PaymentMethod.CASH) huboEfectivo = true;
            if (pago.getRecibido() != null && pago.getRecibido().compareTo(pago.getMonto()) > 0) {
                recibidoTotal = recibidoTotal.add(pago.getRecibido());
                cambioTotal = cambioTotal.add(pago.getRecibido().subtract(pago.getMonto()));
            }
        }
        // "Pagado" solo aporta algo cuando hay más de un pago (mixto): con uno solo, ya es
        // exactamente el mismo importe que la línea del método de arriba — repetirlo se veía
        // como si el ticket imprimiera el total tres veces (TOTAL/método/Pagado).
        if (pagosActivos > 1) {
            text(buf, padBetween("Pagado", money(cuenta.getTotalPagado())) + "\n");
        }
        if (cambioTotal.signum() > 0) {
            text(buf, padBetween("Recibido", money(recibidoTotal)) + "\n");
            text(buf, padBetween("Cambio", money(cambioTotal)) + "\n");
        }

        text(buf, line());
        ctl(buf, 0x1B, 0x61, 0x01);
        // QR "califica tu visita" (solo cuentas de mesa con página web activa), impreso como
        // imagen raster para que salga en cualquier impresora (ver qrRaster).
        String urlOpinion = null;
        if (conQr) {
            try { urlOpinion = opinionService.enlace(cuenta); } catch (Exception ignored) { /* el ticket sale igual */ }
        }
        if (urlOpinion != null) {
            text(buf, "Que tal estuvo? Califica tu visita\n");
            qrRaster(buf, urlOpinion);
            text(buf, "\n");
        }
        if (porPagar) {
            ctl(buf, 0x1B, 0x45, 0x01);
            text(buf, "Pague con este ticket en caja\n");
            ctl(buf, 0x1B, 0x45, 0x00);
        }
        text(buf, "Gracias por su visita\n");
        ctl(buf, 0x1B, 0x61, 0x00);
        text(buf, "\n\n\n");
        ctl(buf, 0x1D, 0x56, 0x01); // GS V 1 : corte parcial del papel
        return huboEfectivo;
    }

    /** Ancho del QR impreso, en puntos: cabe en papel de 58 mm (384 puntos) y de 80 mm. */
    private static final int QR_PUNTOS = 240;

    /**
     * Código QR como IMAGEN en mapa de bits (GS v 0), no con el comando de QR nativo (GS ( k):
     * ese comando no lo soportan muchas impresoras económicas (con él, el ticket de mesa no
     * salía), mientras que la impresión de imágenes raster la acepta prácticamente cualquier
     * impresora ESC/POS. El QR se arma con ZXing y se escala a ~{@value #QR_PUNTOS} puntos.
     */
    private void qrRaster(ByteArrayOutputStream buf, String data) {
        BitMatrix m;
        try {
            // tamaño 0 = la matriz mínima (un punto por módulo); se escala abajo a mano
            m = new QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, 0, 0, java.util.Map.of(EncodeHintType.MARGIN, 1));
        } catch (Exception e) {
            return; // sin QR, el ticket sale igual
        }
        int modulos = m.getWidth();
        int escala = Math.max(1, QR_PUNTOS / modulos);
        int puntos = modulos * escala;
        int anchoBytes = (puntos + 7) / 8;
        ctl(buf, 0x1D, 0x76, 0x30, 0x00, anchoBytes & 0xFF, (anchoBytes >> 8) & 0xFF, puntos & 0xFF, (puntos >> 8) & 0xFF);
        for (int y = 0; y < puntos; y++) {
            for (int xb = 0; xb < anchoBytes; xb++) {
                int b = 0;
                for (int bit = 0; bit < 8; bit++) {
                    int x = xb * 8 + bit;
                    if (x < puntos && m.get(x / escala, y / escala)) b |= 0x80 >> bit;
                }
                buf.write(b);
            }
        }
    }

    private String metodoPagoEs(PaymentMethod pm) {
        if (pm == null) return "—";
        return switch (pm) {
            case CASH -> "Efectivo";
            case CARD -> "Tarjeta";
            case TRANSFER -> "Transferencia";
        };
    }

    private void ctl(ByteArrayOutputStream buf, int... codes) {
        for (int c : codes) buf.write(c);
    }

    /** Texto en ISO-8859-1 (Latin-1) — la codificación que la mayoría de impresoras ESC/POS esperan por default. */
    private void text(ByteArrayOutputStream buf, String s) {
        buf.writeBytes(s.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private String line() {
        return "-".repeat(WIDTH) + "\n";
    }

    private String padBetween(String left, String right) {
        int spaces = WIDTH - left.length() - right.length();
        if (spaces < 1) return left + " " + right;
        return left + " ".repeat(spaces) + right;
    }

    private String wrap(String text) {
        if (text == null) return "";
        return text.length() > WIDTH ? text.substring(0, WIDTH) : text;
    }

    private String money(BigDecimal amount) {
        return MONEY_FMT.format(amount != null ? amount : BigDecimal.ZERO);
    }

    private String stripTrailingZeros(BigDecimal qty) {
        return qty.stripTrailingZeros().toPlainString();
    }
}
