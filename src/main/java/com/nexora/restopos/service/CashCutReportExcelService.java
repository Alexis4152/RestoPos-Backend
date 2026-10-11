package com.nexora.restopos.service;

import com.nexora.restopos.model.*;
import com.nexora.restopos.repository.PagoRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Genera el reporte Excel (.xlsx, vía Apache POI) del cierre de caja diario de una tienda,
 * consumido por {@link CashCutReportNotifier} para adjuntarlo al correo que reciben los
 * administradores.
 *
 * <p>El libro tiene tres hojas:</p>
 * <ul>
 *   <li><b>Resumen</b>: una fila por cada corte cerrado (cajero, apertura, cierre, fondo
 *   inicial, ventas, gastos, fondo final) más totales y utilidad del periodo.</li>
 *   <li><b>Ventas</b>: totales por método de pago y el detalle de cada {@link Pago} no
 *   cancelado, agrupados por cajero.</li>
 *   <li><b>Productos</b>: el detalle línea por línea de cada {@link CuentaDetalle} de las
 *   cuentas cobradas en el corte (una vez por cuenta, sin duplicar cuando se pagó en varias
 *   partes).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class CashCutReportExcelService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final PagoRepository pagoRepository;

    public byte[] build(Tienda tienda, List<CashCut> cuts) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            CellStyle header = headerStyle(wb);
            CellStyle bold = boldStyle(wb);
            CellStyle money = moneyStyle(wb);

            buildResumen(wb.createSheet("Resumen"), header, bold, money, tienda, cuts);

            Map<User, List<Pago>> pagosByCajero = new LinkedHashMap<>();
            Map<User, LinkedHashSet<Cuenta>> cuentasByCajero = new LinkedHashMap<>();
            for (CashCut cut : cuts) {
                List<Pago> pagos = pagoRepository.findByCashCutId(cut.getId()).stream()
                        .filter(p -> !Boolean.TRUE.equals(p.getCancelado()))
                        .toList();
                pagosByCajero.computeIfAbsent(cut.getUser(), k -> new ArrayList<>()).addAll(pagos);
                LinkedHashSet<Cuenta> cuentas = cuentasByCajero.computeIfAbsent(cut.getUser(), k -> new LinkedHashSet<>());
                for (Pago p : pagos) cuentas.add(p.getCuenta());
            }

            buildVentas(wb.createSheet("Ventas"), header, bold, money, pagosByCajero);
            buildProductos(wb.createSheet("Productos"), header, bold, money, tienda, cuentasByCajero);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("No se pudo generar el reporte de cierre en Excel", e);
        }
    }

    private void buildResumen(Sheet sheet, CellStyle header, CellStyle bold, CellStyle money,
                               Tienda tienda, List<CashCut> cuts) {
        int r = 0;
        row(sheet, r++, bold, "TIENDA", tienda.getName());
        r++;
        header(sheet, r++, header, "CAJERO", "APERTURA", "CIERRE", "FONDO INICIAL", "TOTAL VENTAS", "GASTOS", "FONDO FINAL");

        BigDecimal totalVentas = BigDecimal.ZERO, totalGastos = BigDecimal.ZERO, totalFondoInicial = BigDecimal.ZERO;
        for (CashCut cut : cuts) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(cut.getUser().getName());
            row.createCell(1).setCellValue(cut.getOpenedAt() != null ? cut.getOpenedAt().format(FMT) : "—");
            row.createCell(2).setCellValue(cut.getClosedAt() != null ? cut.getClosedAt().format(FMT) : "—");
            moneyCell(row, 3, money, cut.getOpeningAmount());
            moneyCell(row, 4, money, cut.getTotalSales());
            moneyCell(row, 5, money, cut.getExpenses());
            moneyCell(row, 6, money, cut.getClosingAmount());
            totalVentas = totalVentas.add(nz(cut.getTotalSales()));
            totalGastos = totalGastos.add(nz(cut.getExpenses()));
            totalFondoInicial = totalFondoInicial.add(nz(cut.getOpeningAmount()));
        }

        Row totalRow = sheet.createRow(r++);
        totalRow.createCell(0).setCellValue("TOTAL");
        cellStyle(totalRow, 0, bold);
        moneyCell(totalRow, 3, money, totalFondoInicial);
        moneyCell(totalRow, 4, money, totalVentas);
        moneyCell(totalRow, 5, money, totalGastos);
        moneyCell(totalRow, 6, money, totalFondoInicial.add(totalVentas).subtract(totalGastos));

        r++;
        Row utilidadRow = sheet.createRow(r);
        utilidadRow.createCell(0).setCellValue("UTILIDAD");
        cellStyle(utilidadRow, 0, bold);
        moneyCell(utilidadRow, 1, money, totalVentas.subtract(totalGastos));

        autosize(sheet, 7);
    }

    private void buildVentas(Sheet sheet, CellStyle header, CellStyle bold, CellStyle money,
                              Map<User, List<Pago>> pagosByCajero) {
        int r = 0;

        Map<PaymentMethod, long[]> counts = new EnumMap<>(PaymentMethod.class);
        Map<PaymentMethod, BigDecimal> totals = new EnumMap<>(PaymentMethod.class);
        for (PaymentMethod pm : PaymentMethod.values()) {
            counts.put(pm, new long[]{0});
            totals.put(pm, BigDecimal.ZERO);
        }
        BigDecimal grandTotal = BigDecimal.ZERO;
        for (List<Pago> pagos : pagosByCajero.values()) {
            for (Pago p : pagos) {
                counts.get(p.getMetodo())[0]++;
                totals.put(p.getMetodo(), totals.get(p.getMetodo()).add(p.getMonto()));
                grandTotal = grandTotal.add(p.getMonto());
            }
        }
        r += 2;
        for (PaymentMethod pm : PaymentMethod.values()) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(spanishMethod(pm));
            row.createCell(1).setCellValue(counts.get(pm)[0]);
            moneyCell(row, 2, money, totals.get(pm));
        }
        Row totalRow = sheet.createRow(r++);
        totalRow.createCell(0).setCellValue("TOTAL");
        cellStyle(totalRow, 0, bold);
        moneyCell(totalRow, 2, money, grandTotal);
        r += 2;

        header(sheet, r++, header, "ID_PAGO", "FECHA", "CUENTA", "METODO", "COBRÓ", "MONTO");
        for (Map.Entry<User, List<Pago>> entry : pagosByCajero.entrySet()) {
            for (Pago p : entry.getValue()) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(p.getId());
                row.createCell(1).setCellValue(p.getCreatedAt() != null ? p.getCreatedAt().format(FMT) : "—");
                row.createCell(2).setCellValue(p.getCuenta().getTicketFolio() != null ? p.getCuenta().getTicketFolio() : "C-" + p.getCuenta().getId());
                row.createCell(3).setCellValue(spanishMethod(p.getMetodo()));
                row.createCell(4).setCellValue(entry.getKey().getName());
                moneyCell(row, 5, money, p.getMonto());
            }
        }
        autosize(sheet, 6);
    }

    private void buildProductos(Sheet sheet, CellStyle header, CellStyle bold, CellStyle money,
                                 Tienda tienda, Map<User, LinkedHashSet<Cuenta>> cuentasByCajero) {
        int r = 0;
        header(sheet, r++, header, "CUENTA", "FECHA", "PRODUCTO", "CANTIDAD", "COBRÓ", "TIENDA", "SUBTOTAL");

        BigDecimal grandTotal = BigDecimal.ZERO;
        for (Map.Entry<User, LinkedHashSet<Cuenta>> entry : cuentasByCajero.entrySet()) {
            for (Cuenta cuenta : entry.getValue()) {
                for (CuentaDetalle item : cuenta.getItems()) {
                    Row row = sheet.createRow(r++);
                    row.createCell(0).setCellValue(cuenta.getTicketFolio() != null ? cuenta.getTicketFolio() : "C-" + cuenta.getId());
                    row.createCell(1).setCellValue(cuenta.getCreatedAt() != null ? cuenta.getCreatedAt().format(FMT) : "—");
                    row.createCell(2).setCellValue(item.getOrdenDetalle().getProductoNombre());
                    row.createCell(3).setCellValue(item.getCantidad().doubleValue());
                    row.createCell(4).setCellValue(entry.getKey().getName());
                    row.createCell(5).setCellValue(tienda.getName());
                    moneyCell(row, 6, money, item.getMonto());
                    grandTotal = grandTotal.add(item.getMonto());
                }
            }
        }
        Row totalRow = sheet.createRow(r);
        totalRow.createCell(0).setCellValue("TOTAL");
        cellStyle(totalRow, 0, bold);
        moneyCell(totalRow, 6, money, grandTotal);

        autosize(sheet, 7);
    }

    private String spanishMethod(PaymentMethod pm) {
        return switch (pm) {
            case CASH -> "EFECTIVO";
            case CARD -> "TARJETA";
            case TRANSFER -> "TRANSFERENCIAS";
        };
    }

    private BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private void row(Sheet sheet, int r, CellStyle style, String label, String value) {
        Row row = sheet.createRow(r);
        row.createCell(0).setCellValue(label);
        cellStyle(row, 0, style);
        row.createCell(1).setCellValue(value);
    }

    private void header(Sheet sheet, int r, CellStyle style, String... labels) {
        Row row = sheet.createRow(r);
        for (int i = 0; i < labels.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(labels[i]);
            cell.setCellStyle(style);
        }
    }

    private void moneyCell(Row row, int col, CellStyle style, BigDecimal value) {
        Cell cell = row.createCell(col);
        cell.setCellValue(nz(value).doubleValue());
        cell.setCellStyle(style);
    }

    private void cellStyle(Row row, int col, CellStyle style) {
        Cell cell = row.getCell(col);
        if (cell == null) cell = row.createCell(col);
        cell.setCellStyle(style);
    }

    private void autosize(Sheet sheet, int cols) {
        for (int i = 0; i < cols; i++) sheet.autoSizeColumn(i);
    }

    private CellStyle headerStyle(Workbook wb) {
        Font f = wb.createFont();
        f.setBold(true);
        f.setColor(IndexedColors.WHITE.getIndex());
        CellStyle s = wb.createCellStyle();
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return s;
    }

    private CellStyle boldStyle(Workbook wb) {
        Font f = wb.createFont();
        f.setBold(true);
        CellStyle s = wb.createCellStyle();
        s.setFont(f);
        return s;
    }

    private CellStyle moneyStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
        return s;
    }
}
