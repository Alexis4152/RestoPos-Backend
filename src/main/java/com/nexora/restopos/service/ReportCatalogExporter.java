package com.nexora.restopos.service;

import com.nexora.restopos.dto.catalog.ReportCatalogDtos.Column;
import com.nexora.restopos.dto.catalog.ReportCatalogDtos.ExportFile;
import com.nexora.restopos.dto.catalog.ReportCatalogDtos.Metric;
import com.nexora.restopos.dto.catalog.ReportCatalogDtos.Report;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Exporta cualquier {@link Report} del catálogo (ver {@link ReportCatalogService}) a CSV,
 * Excel (.xlsx) o PDF — los tres formatos muestran exactamente las mismas filas y columnas
 * que la pantalla, nunca un resumen distinto.
 */
@Service
public class ReportCatalogExporter {

    private static final Locale MX = Locale.forLanguageTag("es-MX");

    public ExportFile export(Report report, String format) {
        String base = "reporte-" + report.type() + "-" + report.from() + "-a-" + report.to();
        return switch (format.toLowerCase(Locale.ROOT)) {
            case "csv" -> new ExportFile(base + ".csv", "text/csv; charset=UTF-8", csv(report));
            case "xlsx" -> new ExportFile(base + ".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx(report));
            case "pdf" -> new ExportFile(base + ".pdf", "application/pdf", pdf(report));
            default -> throw new IllegalArgumentException("Formato de exportación no válido: " + format);
        };
    }

    // ── CSV ───────────────────────────────────────────────────────────────────────────────

    private byte[] csv(Report report) {
        StringBuilder sb = new StringBuilder();
        // BOM UTF-8: sin esto Excel en Windows abre acentos/ñ como caracteres sueltos.
        sb.append('﻿');
        sb.append(report.columns().stream().map(c -> csvCell(c.label())).reduce((a, b) -> a + "," + b).orElse(""));
        sb.append("\r\n");
        for (Map<String, Object> row : report.rows()) {
            sb.append(report.columns().stream().map(c -> csvCell(plain(row.get(c.key())))).reduce((a, b) -> a + "," + b).orElse(""));
            sb.append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String csvCell(String value) {
        if (value == null) return "";
        String v = value.replace("\"", "\"\"");
        return v.contains(",") || v.contains("\"") || v.contains("\n") ? "\"" + v + "\"" : v;
    }

    // ── Excel ─────────────────────────────────────────────────────────────────────────────

    private byte[] xlsx(Report report) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Reporte");
            CellStyle header = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font boldFont = wb.createFont();
            boldFont.setBold(true);
            header.setFont(boldFont);
            CellStyle moneyStyle = wb.createCellStyle();
            moneyStyle.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));

            int r = 0;
            Row headerRow = sheet.createRow(r++);
            List<Column> columns = report.columns();
            for (int i = 0; i < columns.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns.get(i).label());
                cell.setCellStyle(header);
            }
            for (Map<String, Object> row : report.rows()) {
                Row xRow = sheet.createRow(r++);
                for (int i = 0; i < columns.size(); i++) {
                    Column col = columns.get(i);
                    Cell cell = xRow.createCell(i);
                    Object value = row.get(col.key());
                    if (value instanceof Number n && !"text".equals(col.format())) {
                        cell.setCellValue(n.doubleValue());
                        if ("money".equals(col.format())) cell.setCellStyle(moneyStyle);
                    } else {
                        cell.setCellValue(plain(value));
                    }
                }
            }
            for (int i = 0; i < columns.size(); i++) sheet.autoSizeColumn(i);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("No se pudo generar el Excel del reporte", e);
        }
    }

    // ── PDF ───────────────────────────────────────────────────────────────────────────────

    private byte[] pdf(Report report) {
        try {
            Document doc = new Document(report.columns().size() > 5 ? PageSize.A4.rotate() : PageSize.A4, 24, 24, 24, 24);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfWriter.getInstance(doc, out);
            doc.open();

            Font titleFont = new Font(Font.HELVETICA, 16, Font.BOLD);
            Font subFont = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.GRAY);
            Paragraph title = new Paragraph(report.title() != null ? report.title() : report.type(), titleFont);
            doc.add(title);
            Paragraph sub = new Paragraph(report.from() + " – " + report.to(), subFont);
            sub.setSpacingAfter(10);
            doc.add(sub);

            if (!report.metrics().isEmpty()) {
                PdfPTable metricsTable = new PdfPTable(report.metrics().size());
                metricsTable.setWidthPercentage(100);
                metricsTable.setSpacingAfter(14);
                for (Metric m : report.metrics()) {
                    PdfPCell cell = new PdfPCell();
                    cell.setBorderColor(new Color(230, 230, 230));
                    cell.setPadding(8);
                    Paragraph p = new Paragraph();
                    p.add(new Paragraph(m.label(), new Font(Font.HELVETICA, 8, Font.NORMAL, Color.GRAY)));
                    p.add(new Paragraph(formatValue(m.value(), m.format(), report.currency()), new Font(Font.HELVETICA, 13, Font.BOLD)));
                    cell.addElement(p);
                    metricsTable.addCell(cell);
                }
                doc.add(metricsTable);
            }

            List<Column> columns = report.columns();
            PdfPTable table = new PdfPTable(columns.size());
            table.setWidthPercentage(100);
            Font headFont = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);
            for (Column col : columns) {
                PdfPCell cell = new PdfPCell(new Paragraph(col.label(), headFont));
                cell.setBackgroundColor(new Color(40, 40, 50));
                cell.setPadding(5);
                table.addCell(cell);
            }
            Font cellFont = new Font(Font.HELVETICA, 8, Font.NORMAL);
            for (Map<String, Object> row : report.rows()) {
                for (Column col : columns) {
                    PdfPCell cell = new PdfPCell(new Paragraph(formatValue(row.get(col.key()), col.format(), report.currency()), cellFont));
                    cell.setPadding(4);
                    if (!"text".equals(col.format())) cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                    table.addCell(cell);
                }
            }
            doc.add(table);

            if (report.truncated()) {
                Paragraph note = new Paragraph("Se muestran " + report.rows().size() + " de " + report.totalRows() + " registros.",
                        new Font(Font.HELVETICA, 8, Font.ITALIC, Color.GRAY));
                note.setSpacingBefore(8);
                doc.add(note);
            }
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("No se pudo generar el PDF del reporte", e);
        }
    }

    // ── Formato de valores (compartido por los tres formatos) ───────────────────────────────

    private String plain(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String formatValue(Object value, String format, String currency) {
        if (value == null) return "—";
        return switch (format) {
            case "money" -> moneyFormat(currency).format(toDouble(value));
            case "percent", "rate" -> String.format(MX, "%.1f%%", toDouble(value));
            case "count", "number" -> NumberFormat.getNumberInstance(MX).format(toDouble(value));
            default -> String.valueOf(value);
        };
    }

    private NumberFormat moneyFormat(String currency) {
        NumberFormat f = NumberFormat.getCurrencyInstance(MX);
        try {
            f.setCurrency(java.util.Currency.getInstance(currency));
        } catch (Exception ignored) {
            // moneda desconocida: se queda con MXN (default del locale es-MX)
        }
        return f;
    }

    private double toDouble(Object value) {
        if (value instanceof BigDecimal b) return b.doubleValue();
        if (value instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
