package com.nexora.restopos.service;

import com.nexora.restopos.model.CuentaDetalle;
import com.nexora.restopos.model.Cuenta;
import com.nexora.restopos.model.Orden;
import com.nexora.restopos.model.OrdenDetalleModificador;
import com.nexora.restopos.model.Pago;
import com.nexora.restopos.model.PaymentMethod;
import com.nexora.restopos.model.Tienda;
import com.nexora.restopos.model.TiendaInfo;
import com.nexora.restopos.repository.TiendaInfoRepository;
import com.nexora.restopos.util.SpanishNumberToWords;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Genera el ticket de una {@link Cuenta} (total, por comensal, o personalizada) en PDF, usado
 * para adjuntarse al correo del cliente ({@link EmailService#sendTicketEmail}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TicketPdfService {

    private static final DateTimeFormatter FECHA_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final NumberFormat MONEY_FMT = NumberFormat.getCurrencyInstance(new Locale("es", "MX"));

    private static final Color GRAY_TEXT = new Color(90, 90, 90);
    private static final Color GRAY_LINE = new Color(190, 190, 190);
    private static final Color GRAY_FILL = new Color(238, 238, 238);

    private static final Font FONT_STORE_NAME = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15);
    private static final Font FONT_SUBTLE = FontFactory.getFont(FontFactory.HELVETICA, 8, GRAY_TEXT);
    private static final Font FONT_BODY = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Font FONT_BODY_BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
    private static final Font FONT_TOTAL = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13);

    private final TiendaInfoRepository tiendaInfoRepository;
    private final OpinionService opinionService;

    @Value("${app.uploads.dir}")
    private String uploadsDir;

    public byte[] generate(Cuenta cuenta) {
        Orden orden = cuenta.getOrden();
        Tienda tienda = orden.getTienda();
        TiendaInfo info = tienda != null ? tiendaInfoRepository.findByTiendaId(tienda.getId()).orElse(null) : null;

        Document document = new Document(PageSize.A5, 36, 36, 24, 24);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            // ── Encabezado ───────────────────────────────────────────────────
            addLogo(document, tienda);
            document.add(paragraph(tienda != null ? tienda.getName() : "RestoPOS", FONT_STORE_NAME, Element.ALIGN_CENTER, 4, 2));
            if (info != null && info.getRfc() != null && !info.getRfc().isBlank()) {
                document.add(paragraph("RFC: " + info.getRfc(), FONT_SUBTLE, Element.ALIGN_CENTER, 0, 0));
            }
            addAddressLines(document, info);
            if (cuenta.getEstado() == com.nexora.restopos.model.CuentaEstado.PENDIENTE) {
                document.add(paragraph("CUENTA POR PAGAR — no es comprobante de pago", FONT_BODY, Element.ALIGN_CENTER, 6, 0));
                document.add(paragraph("Pague con este ticket en caja", FONT_SUBTLE, Element.ALIGN_CENTER, 0, 0));
            }
            addSeparator(document, 10);

            // ── Datos de la cuenta ───────────────────────────────────────────
            String mesero = orden.getMesero() != null ? orden.getMesero().getName() : "—";
            document.add(paragraph("LE ATENDIÓ: " + mesero.toUpperCase(), FONT_BODY, Element.ALIGN_LEFT, 0, 3));
            String fecha = cuenta.getCreatedAt() != null ? cuenta.getCreatedAt().format(FECHA_FMT) : "—";
            String hora = cuenta.getCreatedAt() != null ? cuenta.getCreatedAt().format(HORA_FMT) : "—";
            document.add(paragraph("FECHA: " + fecha + "     HORA: " + hora, FONT_BODY, Element.ALIGN_LEFT, 0, 3));
            document.add(paragraph("FOLIO: " + (cuenta.getTicketFolio() != null ? cuenta.getTicketFolio() : cuenta.getId()), FONT_BODY, Element.ALIGN_LEFT, 0, 3));
            document.add(paragraph(orden.getMesa() != null ? "MESA: " + orden.getMesa().getNumero() : "PARA LLEVAR", FONT_BODY, Element.ALIGN_LEFT, 0, 3));
            if (cuenta.getComensal() != null) {
                String nombreComensal = cuenta.getComensal().getNombre();
                document.add(paragraph("Comensal " + cuenta.getComensal().getNumero() + (nombreComensal != null && !nombreComensal.isBlank() ? " (" + nombreComensal + ")" : ""), FONT_BODY, Element.ALIGN_LEFT, 0, 0));
            }
            addSeparator(document, 10);

            // ── Cuerpo: detalle de la cuenta ─────────────────────────────────
            PdfPTable table = new PdfPTable(new float[]{4, 1, 2, 2});
            table.setWidthPercentage(100);
            table.setSpacingAfter(10);
            addHeaderCell(table, "Producto", Element.ALIGN_LEFT);
            addHeaderCell(table, "Cant.", Element.ALIGN_CENTER);
            addHeaderCell(table, "P. Unit.", Element.ALIGN_RIGHT);
            addHeaderCell(table, "Subtotal", Element.ALIGN_RIGHT);

            for (CuentaDetalle item : cuenta.getItems()) {
                BigDecimal unitario = item.getCantidad().compareTo(BigDecimal.ZERO) > 0
                        ? item.getMonto().divide(item.getCantidad(), 2, java.math.RoundingMode.HALF_UP)
                        : item.getMonto();
                table.addCell(cell(item.getOrdenDetalle().getProductoNombre(), Element.ALIGN_LEFT));
                table.addCell(cell(item.getCantidad().stripTrailingZeros().toPlainString(), Element.ALIGN_CENTER));
                table.addCell(cell(money(unitario), Element.ALIGN_RIGHT));
                table.addCell(cell(money(item.getMonto()), Element.ALIGN_RIGHT));

                for (OrdenDetalleModificador mod : item.getOrdenDetalle().getModificadores()) {
                    boolean tieneCosto = mod.getPrecioExtra().compareTo(BigDecimal.ZERO) > 0;
                    table.addCell(cellSubtle("   " + mod.getGrupoNombre() + " " + mod.getNombre(), Element.ALIGN_LEFT));
                    table.addCell(cellSubtle("", Element.ALIGN_CENTER));
                    table.addCell(cellSubtle("", Element.ALIGN_RIGHT));
                    table.addCell(cellSubtle(tieneCosto ? money(mod.getPrecioExtra()) : "", Element.ALIGN_RIGHT));
                }
            }
            document.add(table);

            // ── Totales y formas de pago ─────────────────────────────────────
            if (cuenta.getPropina() != null && cuenta.getPropina().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal subtotal = cuenta.getTotal().subtract(cuenta.getPropina());
                String etiquetaPropina = cuenta.getPropinaPorcentaje() != null
                        ? "Propina (" + cuenta.getPropinaPorcentaje().stripTrailingZeros().toPlainString() + "%)"
                        : "Propina";
                document.add(paragraph("Subtotal: " + money(subtotal), FONT_BODY, Element.ALIGN_RIGHT, 4, 2));
                document.add(paragraph(etiquetaPropina + ": " + money(cuenta.getPropina()), FONT_BODY, Element.ALIGN_RIGHT, 0, 2));
            }
            document.add(paragraph("Total: " + money(cuenta.getTotal()), FONT_TOTAL, Element.ALIGN_RIGHT, 4, 6));
            BigDecimal recibidoTotal = BigDecimal.ZERO;
            BigDecimal cambioTotal = BigDecimal.ZERO;
            int pagosActivos = 0;
            for (Pago pago : cuenta.getPagos()) {
                if (Boolean.TRUE.equals(pago.getCancelado())) continue;
                pagosActivos++;
                document.add(paragraph(metodoPagoEs(pago.getMetodo()) + ": " + money(pago.getMonto()), FONT_BODY, Element.ALIGN_RIGHT, 0, 2));
                if (pago.getRecibido() != null && pago.getRecibido().compareTo(pago.getMonto()) > 0) {
                    recibidoTotal = recibidoTotal.add(pago.getRecibido());
                    cambioTotal = cambioTotal.add(pago.getRecibido().subtract(pago.getMonto()));
                }
            }
            // "Pagado" solo aporta algo cuando hay más de un pago (mixto): con uno solo, ya
            // es exactamente el mismo importe que la línea del método de arriba — repetirlo
            // se veía como si el ticket imprimiera el total tres veces (Total/método/Pagado).
            if (pagosActivos > 1) {
                document.add(paragraph("Pagado: " + money(cuenta.getTotalPagado()), FONT_BODY_BOLD, Element.ALIGN_RIGHT, 4, 2));
            }
            if (cambioTotal.compareTo(BigDecimal.ZERO) > 0) {
                document.add(paragraph("Recibido: " + money(recibidoTotal), FONT_BODY, Element.ALIGN_RIGHT, 0, 2));
                document.add(paragraph("Cambio: " + money(cambioTotal), FONT_BODY_BOLD, Element.ALIGN_RIGHT, 0, 2));
            }
            addSeparator(document, 10);

            // ── Pie ────────────────────────────────────────────────────────
            document.add(paragraph(SpanishNumberToWords.pesos(cuenta.getTotal()), FONT_BODY, Element.ALIGN_CENTER, 0, 10));
            addSeparator(document, 10);
            addQrOpinion(document, cuenta);
            document.add(paragraph("NEXORA SYSTEMS", FONT_BODY_BOLD, Element.ALIGN_CENTER, 0, 2));
            document.add(paragraph("WWW.NEXORASYSTEMS.COM", FONT_SUBTLE, Element.ALIGN_CENTER, 0, 6));
            document.add(paragraph("¡¡¡GRACIAS POR SU VISITA, VUELVA PRONTO!!!", FONT_SUBTLE, Element.ALIGN_CENTER, 0, 0));

            document.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el ticket en PDF", e);
        }
        return out.toByteArray();
    }

    private String metodoPagoEs(PaymentMethod pm) {
        if (pm == null) return "—";
        return switch (pm) {
            case CASH -> "Efectivo";
            case CARD -> "Tarjeta";
            case TRANSFER -> "Transferencia";
        };
    }

    private void addAddressLines(Document document, TiendaInfo info) throws DocumentException {
        if (info == null) return;
        addIfPresent(document, info.getCalle());
        addIfPresent(document, info.getColonia());
        addIfPresent(document, info.getCodigoPostal() != null && !info.getCodigoPostal().isBlank() ? "C.P. " + info.getCodigoPostal() : null);
        addIfPresent(document, info.getLocalidad());
        addIfPresent(document, info.getEstado());
    }

    private void addIfPresent(Document document, String text) throws DocumentException {
        if (text != null && !text.isBlank()) {
            document.add(paragraph(text, FONT_SUBTLE, Element.ALIGN_CENTER, 0, 0));
        }
    }

    /** QR "¿Qué tal estuvo? Califícanos" (solo cuentas de mesa con página web activa). */
    private void addQrOpinion(Document document, Cuenta cuenta) {
        try {
            String url = opinionService.enlace(cuenta);
            if (url == null) return;
            document.add(paragraph("¿QUÉ TAL ESTUVO? ESCANEA Y CALIFÍCANOS", FONT_BODY_BOLD, Element.ALIGN_CENTER, 0, 4));
            Image qr = Image.getInstance(OpinionService.qrPng(url, 300));
            qr.scaleToFit(95, 95);
            qr.setAlignment(Image.ALIGN_CENTER);
            document.add(qr);
            addSeparator(document, 10);
        } catch (Exception e) {
            log.warn("No se pudo agregar el QR de opinión al ticket: {}", e.getMessage());
        }
    }

    private void addLogo(Document document, Tienda tienda) {
        try {
            byte[] bytes = logoBytes(tienda);
            Image image = Image.getInstance(bytes);
            image.scaleToFit(70, 70);
            image.setAlignment(Image.ALIGN_CENTER);
            document.add(image);
        } catch (Exception e) {
            log.warn("No se pudo cargar el logo para el ticket: {}", e.getMessage());
        }
    }

    private byte[] logoBytes(Tienda tienda) throws IOException {
        if (tienda != null && tienda.getLogoPath() != null && !tienda.getLogoPath().isBlank()) {
            Path path = Paths.get(uploadsDir, tienda.getLogoPath().replaceFirst("^/uploads/", ""));
            if (Files.exists(path)) {
                return Files.readAllBytes(path);
            }
        }
        try (InputStream in = new ClassPathResource("branding/default-logo.png").getInputStream()) {
            return in.readAllBytes();
        }
    }

    private void addSeparator(Document document, float spacingAfter) throws DocumentException {
        LineSeparator line = new LineSeparator(0.5f, 100, GRAY_LINE, Element.ALIGN_CENTER, 0);
        Paragraph p = new Paragraph();
        p.add(new Chunk(line));
        p.setSpacingAfter(spacingAfter);
        document.add(p);
    }

    private void addHeaderCell(PdfPTable table, String text, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FONT_BODY_BOLD));
        cell.setBackgroundColor(GRAY_FILL);
        cell.setHorizontalAlignment(align);
        cell.setPadding(5);
        table.addCell(cell);
    }

    private PdfPCell cell(String text, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FONT_BODY));
        cell.setHorizontalAlignment(align);
        cell.setPadding(5);
        return cell;
    }

    /** Como {@link #cell}, pero para la sub-línea de un modificador elegido: fuente más chica y gris. */
    private PdfPCell cellSubtle(String text, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FONT_SUBTLE));
        cell.setHorizontalAlignment(align);
        cell.setPadding(4);
        return cell;
    }

    private Paragraph paragraph(String text, Font font, int align, float spacingBefore, float spacingAfter) {
        Paragraph p = new Paragraph(text, font);
        p.setAlignment(align);
        p.setSpacingBefore(spacingBefore);
        p.setSpacingAfter(spacingAfter);
        return p;
    }

    private String money(BigDecimal amount) {
        return MONEY_FMT.format(amount != null ? amount : BigDecimal.ZERO);
    }
}
