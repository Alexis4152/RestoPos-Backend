package com.nexora.restopos.service;

import com.nexora.restopos.model.Comanda;
import com.nexora.restopos.model.Orden;
import com.nexora.restopos.model.OrdenDetalle;
import com.nexora.restopos.model.OrdenDetalleModificador;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Ticket de comanda (lo que se mandó de una orden) en ESC/POS, para la impresora de cocina o
 * la de tickets. Sin precios: solo qué preparar, para quién y con qué notas — en letra grande
 * para que se lea de lejos en la cocina. Mismo ancho (32 columnas) que el ticket de cuenta.
 */
@Service
public class ComandaTicketService {

    private static final int WIDTH = 32;
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    public String build(Comanda comanda, Orden orden, List<OrdenDetalle> lineas) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        ctl(buf, 0x1B, 0x40);                 // ESC @ : inicializa
        ctl(buf, 0x1B, 0x61, 0x01);           // centro
        // Mismo tamaño de letra que el ticket de venta (normal); los encabezados van en negritas.
        ctl(buf, 0x1D, 0x21, 0x00);           // tamaño normal
        ctl(buf, 0x1B, 0x45, 0x01);           // negritas
        text(buf, "COCINA".equals(comanda.getDestino()) ? "COCINA\n" : "ORDEN\n");
        text(buf, wrap(destinoOrden(orden)) + "\n");
        ctl(buf, 0x1B, 0x45, 0x00);
        text(buf, "Orden #" + orden.getId() + "\n");
        ctl(buf, 0x1B, 0x61, 0x00);           // izquierda
        String hora = comanda.getCreatedAt() != null ? HORA.format(comanda.getCreatedAt()) : "";
        text(buf, padBetween(wrap(comanda.getEnviadaPor() != null ? comanda.getEnviadaPor() : ""), hora) + "\n");
        text(buf, "-".repeat(WIDTH) + "\n");

        for (OrdenDetalle d : lineas) {
            ctl(buf, 0x1B, 0x45, 0x01);       // cantidad y platillo en negritas
            for (String l : partir(cantidad(d.getCantidad()) + " x " + d.getProductoNombre())) text(buf, l + "\n");
            ctl(buf, 0x1B, 0x45, 0x00);
            if (d.getModificadores() != null) {
                // Cocina ve el extra por su nombre ("Cebolla x3", "Aguacate"), sin marcar si es libre.
                for (com.nexora.restopos.util.ModsAgrupados.Mod m : com.nexora.restopos.util.ModsAgrupados.agrupar(d.getModificadores())) {
                    for (String l : partir("  + " + m.nombreConCantidad())) text(buf, l + "\n");
                }
            }
            if (d.getComensal() != null) {
                text(buf, "  Comensal " + d.getComensal().getNumero()
                        + (d.getComensal().getNombre() != null ? " (" + d.getComensal().getNombre() + ")" : "") + "\n");
            }
            if (d.getObservaciones() != null && !d.getObservaciones().isBlank()) {
                ctl(buf, 0x1B, 0x45, 0x01);
                for (String l : partir("  Nota: " + d.getObservaciones().trim())) text(buf, l + "\n");
                ctl(buf, 0x1B, 0x45, 0x00);
            }
            text(buf, "\n");
        }
        text(buf, "-".repeat(WIDTH) + "\n\n\n\n");
        ctl(buf, 0x1D, 0x56, 0x01);           // GS V 1 : corte parcial
        return toHex(buf.toByteArray());
    }

    private static String destinoOrden(Orden orden) {
        if (orden.getMesa() != null) {
            String n = orden.getMesa().getNumero();
            return n != null && n.toLowerCase().startsWith("mesa") ? n : "Mesa " + n;
        }
        String tipo = OrdenService.etiquetaTipoEntrega(orden.getTipoEntrega());
        return orden.getClienteNombre() != null && !orden.getClienteNombre().isBlank()
                ? tipo + " - " + orden.getClienteNombre() : tipo;
    }

    private static String cantidad(BigDecimal q) {
        return q == null ? "1" : q.stripTrailingZeros().toPlainString();
    }

    /** Parte un texto largo en renglones del ancho del ticket (sin cortar el contenido). */
    private static List<String> partir(String s) {
        String t = s == null ? "" : s;
        if (t.length() <= WIDTH) return List.of(t);
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        while (t.length() > WIDTH) {
            int corte = t.lastIndexOf(' ', WIDTH);
            if (corte <= 4) corte = WIDTH;
            out.add(t.substring(0, corte));
            t = "    " + t.substring(corte).trim();
        }
        out.add(t);
        return out;
    }

    private static String wrap(String s) {
        return s.length() > WIDTH ? s.substring(0, WIDTH) : s;
    }

    private static String padBetween(String left, String right) {
        int spaces = WIDTH - left.length() - right.length();
        return spaces < 1 ? left + " " + right : left + " ".repeat(spaces) + right;
    }

    private static void ctl(ByteArrayOutputStream buf, int... codes) {
        for (int c : codes) buf.write(c);
    }

    /** Latin-1 (lo que esperan casi todas las térmicas); lo que no cabe ahí se cambia por su letra base. */
    private static void text(ByteArrayOutputStream buf, String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (char ch : s.toCharArray()) {
            if (ch <= 0xFF) out.append(ch);
            else out.append(Normalizer.normalize(String.valueOf(ch), Normalizer.Form.NFD).replaceAll("[^\\x00-\\xFF]", ""));
        }
        buf.writeBytes(out.toString().getBytes(StandardCharsets.ISO_8859_1));
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
