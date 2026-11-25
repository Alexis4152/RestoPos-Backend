package com.nexora.restopos.service;

import com.nexora.restopos.dto.CashCutSummary;
import com.nexora.restopos.model.*;
import com.nexora.restopos.repository.CashMovementRepository;
import com.nexora.restopos.repository.PagoRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;

/**
 * Genera el reporte Excel (.xlsx, vía Apache POI) del cierre de caja, consumido por
 * {@link CashCutReportNotifier} para adjuntarlo al correo que reciben los administradores.
 * Mismo formato que DemoPV, adaptado a cobros de restaurante.
 *
 * <p>El libro tiene cuatro hojas:</p>
 * <ul>
 *   <li><b>Cómo se calcula</b>: qué significa cada dato del Resumen.</li>
 *   <li><b>Resumen</b>: una columna por turno y otra con el total del día, en bloques Ventas,
 *   Devoluciones, Utilidad, Efectivo en caja y Antes de abrir el turno.</li>
 *   <li><b>Cobros</b>: totales por método, devoluciones por método, y el detalle de cada cobro
 *   (las devoluciones en negativo). No incluye los cobros cancelados.</li>
 *   <li><b>Productos</b>: cada platillo de las cuentas cobradas en el reporte, con su categoría
 *   y descuento (una vez por cuenta, aunque se haya pagado en varias partes).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class CashCutReportExcelService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");

    private final PagoRepository pagoRepository;
    private final CashBalanceCalculator calculator;
    private final CashMovementRepository movementRepository;

    /**
     * @param tienda tienda del reporte
     * @param cuts   cortes cerrados a incluir, del más antiguo al más reciente
     * @return el archivo .xlsx en bytes
     */
    public byte[] build(Tienda tienda, List<CashCut> cuts) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            CellStyle header = headerStyle(wb);
            CellStyle bold = boldStyle(wb);
            CellStyle money = moneyStyle(wb);

            buildComoSeCalcula(wb.createSheet("Cómo se calcula"), header, wb);
            Sheet resumen = wb.createSheet("Resumen");

            // Cobros vigentes de cada corte, agrupados por cajero (dueño del corte).
            Map<User, List<Pago>> pagosByCajero = new LinkedHashMap<>();
            for (CashCut cut : cuts) {
                List<Pago> pagos = pagoRepository.findByCashCutId(cut.getId()).stream()
                        .filter(p -> !Boolean.TRUE.equals(p.getCancelado()))
                        .sorted(Comparator.comparing(Pago::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                        .toList();
                pagosByCajero.computeIfAbsent(cut.getUser(), k -> new ArrayList<>()).addAll(pagos);
            }

            buildResumen(resumen, resumenStyles(wb), tienda, cuts);
            buildCobros(wb.createSheet("Cobros"), header, bold, money, pagosByCajero);
            buildProductos(wb.createSheet("Productos"), header, bold, money, tienda, pagosByCajero);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new com.nexora.restopos.exception.OperacionFallidaException("No se pudo generar el reporte de cierre en Excel", e);
        }
    }

    /** Hoja "Cómo se calcula": qué significa cada dato de la hoja Resumen. */
    private void buildComoSeCalcula(Sheet sheet, CellStyle header, Workbook wb) {
        CellStyle wrap = wb.createCellStyle();
        wrap.setWrapText(true);
        wrap.setVerticalAlignment(VerticalAlignment.TOP);
        int r = 0;
        header(sheet, r++, header, "BLOQUE", "DATO", "CÓMO SE CALCULA");
        String[][] rows = {
                {"Columnas", "Turnos", "Cada turno del reporte es una columna, con su cajero y su horario."},
                {"Columnas", "TOTAL DEL DÍA", "Suma de los turnos, excepto en Efectivo en caja: Fondo inicial es el del primer turno; Efectivo esperado y Contado son los del último, porque lo que deja un turno es el fondo del siguiente."},
                {"Ventas", "Efectivo", "Lo cobrado en efectivo en el turno (incluye propinas)."},
                {"Ventas", "Tarjeta", "Lo cobrado con tarjeta en el turno (incluye propinas)."},
                {"Ventas", "Transferencia", "Lo cobrado por transferencia en el turno (incluye propinas)."},
                {"Ventas", "Total ventas", "Efectivo + Tarjeta + Transferencia. No cuenta los cobros cancelados ni resta las devoluciones."},
                {"Devoluciones", "Efectivo", "Efectivo devuelto a clientes en el turno, aunque la cuenta sea de otro día. Sale de la caja."},
                {"Devoluciones", "Tarjeta", "Lo devuelto con tarjeta en el turno. No sale de la caja, así que no cambia el Efectivo esperado."},
                {"Devoluciones", "Transferencia", "Lo devuelto por transferencia en el turno. No sale de la caja, así que no cambia el Efectivo esperado."},
                {"Devoluciones", "Total devoluciones", "Efectivo + Tarjeta + Transferencia."},
                {"Utilidad", "Gastos", "Efectivo que se pagó de la caja en gastos, incluidos los registrados antes de abrir el turno."},
                {"Utilidad", "Utilidad total", "Total ventas − Total devoluciones − Gastos."},
                {"Utilidad", "Utilidad en efectivo", "Ventas en efectivo − Devoluciones en efectivo − Gastos. No incluye tarjeta ni transferencia."},
                {"Efectivo en caja", "Fondo inicial", "Efectivo con el que abrió el turno."},
                {"Efectivo en caja", "+ Ventas en efectivo", "Lo cobrado en efectivo en el turno."},
                {"Efectivo en caja", "+ Entradas", "Efectivo que entró sin ser venta: aportes, entradas de cambio, pagos de faltantes y préstamos recibidos de otras cajas."},
                {"Efectivo en caja", "− Gastos", "Gastos pagados durante el turno. Los registrados antes de abrir ya no están en el Fondo inicial."},
                {"Efectivo en caja", "− Retiros", "Efectivo que se sacó de la caja (entregado al dueño, depósito bancario, caja fuerte u otro)."},
                {"Efectivo en caja", "− Reembolsos", "Efectivo pagado de la caja para reembolsar un aporte."},
                {"Efectivo en caja", "− Préstamos a otras cajas", "Efectivo prestado a otra caja."},
                {"Efectivo en caja", "− Devoluciones en efectivo", "Efectivo devuelto a clientes en el turno."},
                {"Efectivo en caja", "Efectivo esperado", "Fondo inicial + Ventas en efectivo + Entradas − Gastos − Retiros − Reembolsos − Préstamos a otras cajas − Devoluciones en efectivo."},
                {"Efectivo en caja", "Contado", "Efectivo que se contó al cerrar el turno."},
                {"Efectivo en caja", "Diferencia", "Contado − Efectivo esperado. Negativo = faltó dinero; positivo = sobró."},
                {"Antes de abrir el turno", "Movimientos", "Gastos, retiros y entradas registrados con la caja cerrada (por ejemplo, lo que quedó del día anterior y se gastó o se retiró). Ya están descontados del Fondo inicial, así que no cambian el Efectivo esperado."},
        };
        for (String[] data : rows) {
            Row row = sheet.createRow(r++);
            for (int c = 0; c < data.length; c++) {
                row.createCell(c).setCellValue(data[c]);
                cellStyle(row, c, wrap);
            }
        }
        sheet.setColumnWidth(0, 22 * 256);
        sheet.setColumnWidth(1, 28 * 256);
        sheet.setColumnWidth(2, 95 * 256);
    }

    /**
     * Hoja "Resumen": los conceptos hacia abajo, una columna por turno y al final el total. Se
     * recalcula cada turno con {@link CashBalanceCalculator} para que ventas y devoluciones
     * salgan igual en cortes viejos y nuevos.
     */
    private void buildResumen(Sheet sheet, ResumenStyles st, Tienda tienda, List<CashCut> cuts) {
        Map<Long, CashCutSummary> sm = new HashMap<>();
        for (CashCut cut : cuts) sm.put(cut.getId(), calculator.summarize(cut));
        Function<CashCut, CashCutSummary> s = c -> sm.get(c.getId());
        Function<CashCut, BigDecimal> cashRefunds = c -> nz(s.apply(c).getRefundsTotal());
        Function<CashCut, BigDecimal> refunds = c -> cashRefunds.apply(c)
                .add(nz(s.apply(c).getCardRefunds())).add(nz(s.apply(c).getTransferRefunds()));
        Map<Long, List<CashMovement>> beforeOpening = new HashMap<>();
        for (CashCut cut : cuts) beforeOpening.put(cut.getId(), movementRepository.findByReceivedByCutIdOrderByCreatedAtAsc(cut.getId()));
        // Un gasto antes de abrir sí baja la utilidad, aunque ya no pase por el efectivo del turno.
        Function<CashCut, BigDecimal> expenses = c -> beforeOpening.get(c.getId()).stream()
                .filter(m -> m.getType() == CashMovementType.EXPENSE).map(CashMovement::getAmount)
                .reduce(nz(s.apply(c).getExpensesTotal()), BigDecimal::add);

        int r = 0;
        Row title = sheet.createRow(r++);
        title.createCell(0).setCellValue("CORTE DE CAJA");
        cellStyle(title, 0, st.title());
        List<String> registers = cuts.stream().map(this::registerName).distinct().toList();
        row(sheet, r++, st.label(), "Tienda", tienda.getName());
        row(sheet, r++, st.label(), "Caja", String.join(", ", registers));
        row(sheet, r++, st.label(), "Día", reportDay(cuts));
        r++;

        Row head = sheet.createRow(r++);
        head.setHeightInPoints(48);
        head.createCell(0).setCellValue("CONCEPTO");
        cellStyle(head, 0, st.headLeft());
        for (int i = 0; i < cuts.size(); i++) {
            CashCut cut = cuts.get(i);
            String caja = registers.size() > 1 ? registerName(cut) + " · " : "";
            head.createCell(i + 1).setCellValue(caja + "TURNO " + (i + 1) + "\n" + cut.getUser().getName() + "\n" + turnHours(cut));
            cellStyle(head, i + 1, st.headRight());
        }
        int totalCol = cuts.size() + 1;
        head.createCell(totalCol).setCellValue("TOTAL DEL DÍA");
        cellStyle(head, totalCol, st.headRight());
        sheet.createFreezePane(1, r);

        r = section(sheet, r, st, "VENTAS", totalCol);
        r = line(sheet, r, st, false, "Efectivo", cuts, c -> s.apply(c).getCashSales());
        r = line(sheet, r, st, false, "Tarjeta", cuts, c -> s.apply(c).getCardSales());
        r = line(sheet, r, st, false, "Transferencia", cuts, c -> s.apply(c).getTransferSales());
        r = line(sheet, r, st, true, "Total ventas", cuts, c -> s.apply(c).getTotalSales()) + 1;

        r = section(sheet, r, st, "DEVOLUCIONES", totalCol);
        r = line(sheet, r, st, false, "Efectivo", cuts, cashRefunds);
        r = line(sheet, r, st, false, "Tarjeta", cuts, c -> s.apply(c).getCardRefunds());
        r = line(sheet, r, st, false, "Transferencia", cuts, c -> s.apply(c).getTransferRefunds());
        r = line(sheet, r, st, true, "Total devoluciones", cuts, refunds) + 1;

        r = section(sheet, r, st, "UTILIDAD", totalCol);
        r = line(sheet, r, st, false, "Gastos", cuts, expenses);
        r = line(sheet, r, st, true, "Utilidad total", cuts,
                c -> nz(s.apply(c).getTotalSales()).subtract(refunds.apply(c)).subtract(expenses.apply(c)));
        r = line(sheet, r, st, true, "Utilidad en efectivo", cuts,
                c -> nz(s.apply(c).getCashSales()).subtract(cashRefunds.apply(c)).subtract(expenses.apply(c))) + 1;

        // Lo que deja un turno es el fondo del siguiente: en el total cuenta el primero y el
        // último de cada caja (los cortes sin caja no se encadenan: cada uno va por su lado).
        Map<Object, CashCut> firstOfRegister = new LinkedHashMap<>();
        Map<Object, CashCut> lastOfRegister = new LinkedHashMap<>();
        for (CashCut cut : cuts) {
            Object key = cut.getCashRegister() != null ? cut.getCashRegister().getId() : "corte-" + cut.getId();
            firstOfRegister.putIfAbsent(key, cut);
            lastOfRegister.put(key, cut);
        }

        r = section(sheet, r, st, "EFECTIVO EN CAJA", totalCol);
        r = line(sheet, r, st, false, "Fondo inicial", cuts, CashCut::getOpeningAmount, sum(firstOfRegister.values(), CashCut::getOpeningAmount));
        r = line(sheet, r, st, false, "+ Ventas en efectivo", cuts, c -> s.apply(c).getCashSales());
        r = line(sheet, r, st, false, "+ Entradas", cuts, c -> s.apply(c).getEntriesTotal());
        r = line(sheet, r, st, false, "− Gastos", cuts, c -> nz(s.apply(c).getExpensesTotal()).negate());
        r = line(sheet, r, st, false, "− Retiros", cuts, c -> nz(s.apply(c).getWithdrawalsTotal()).negate());
        r = line(sheet, r, st, false, "− Reembolsos", cuts, c -> nz(s.apply(c).getReimbursementsTotal()).negate());
        r = line(sheet, r, st, false, "− Préstamos a otras cajas", cuts, c -> nz(s.apply(c).getLoansOutTotal()).negate());
        r = line(sheet, r, st, false, "− Devoluciones en efectivo", cuts, c -> cashRefunds.apply(c).negate());
        r = line(sheet, r, st, true, "Efectivo esperado", cuts, this::expected, sum(lastOfRegister.values(), this::expected));

        Row counted = sheet.createRow(r++);
        counted.createCell(0).setCellValue("Contado");
        for (int i = 0; i < cuts.size(); i++) countedCell(counted, i + 1, st.money(), cuts.get(i).getCountedAmount());
        boolean allCounted = lastOfRegister.values().stream().allMatch(c -> c.getCountedAmount() != null);
        countedCell(counted, totalCol, st.moneyBold(), allCounted ? sum(lastOfRegister.values(), CashCut::getCountedAmount) : null);
        r = line(sheet, r, st, true, "Diferencia", cuts, CashCut::getCashDifference) + 1;

        if (beforeOpening.values().stream().anyMatch(list -> !list.isEmpty())) {
            r = section(sheet, r, st, "ANTES DE ABRIR EL TURNO", totalCol);
            for (CashCut cut : cuts) {
                for (CashMovement m : beforeOpening.get(cut.getId())) {
                    BigDecimal signed = m.getType().isOutflow() ? m.getAmount().negate() : m.getAmount();
                    r = line(sheet, r, st, false, beforeOpeningLabel(m), cuts, c -> c == cut ? signed : BigDecimal.ZERO);
                    cellStyle(sheet.getRow(r - 1), 0, st.wrap());
                }
            }
        }

        sheet.setColumnWidth(0, 32 * 256);
        for (int c = 1; c <= totalCol; c++) sheet.setColumnWidth(c, 30 * 256);
        sheet.setDisplayGridlines(false);
        sheet.setFitToPage(true);
        sheet.getPrintSetup().setFitWidth((short) 1);
        sheet.getPrintSetup().setFitHeight((short) 1);
    }

    /** Efectivo esperado al cerrar (el guardado en el corte; si falta, el calculado). */
    private BigDecimal expected(CashCut c) {
        return c.getClosingAmount() != null ? c.getClosingAmount() : calculator.expectedCash(c);
    }

    /** Qué fue el movimiento, a dónde fue, quién y a qué hora. */
    private String beforeOpeningLabel(CashMovement m) {
        String what = switch (m.getType()) {
            case WITHDRAWAL -> "Retiro · " + (m.getDestination() != null ? m.getDestination().describe(m.getDestinationOther()) : "—");
            case EXPENSE -> "Gasto · " + m.getConcept();
            default -> "Entrada" + (m.getConcept() != null ? " · " + m.getConcept() : "");
        };
        String person = m.getPerson() != null ? m.getPerson().getName() : m.getRegisteredBy().getName();
        LocalDateTime when = m.getOccurredAt() != null ? m.getOccurredAt() : m.getCreatedAt();
        return what + " · " + person + " · " + when.format(HOUR);
    }

    /** Título de un bloque del Resumen, con su franja a todo lo ancho. */
    private int section(Sheet sheet, int r, ResumenStyles st, String label, int totalCol) {
        Row row = sheet.createRow(r);
        row.createCell(0).setCellValue(label);
        for (int c = 0; c <= totalCol; c++) cellStyle(row, c, st.section());
        return r + 1;
    }

    /** Renglón del Resumen cuyo total es la suma de los turnos. */
    private int line(Sheet sheet, int r, ResumenStyles st, boolean strong, String label,
                     List<CashCut> cuts, Function<CashCut, BigDecimal> value) {
        return line(sheet, r, st, strong, label, cuts, value, sum(cuts, value));
    }

    /** Renglón del Resumen: la etiqueta, el valor de cada turno y el total. @return el siguiente renglón */
    private int line(Sheet sheet, int r, ResumenStyles st, boolean strong, String label,
                     List<CashCut> cuts, Function<CashCut, BigDecimal> value, BigDecimal total) {
        Row row = sheet.createRow(r);
        row.createCell(0).setCellValue(label);
        if (strong) cellStyle(row, 0, st.strongLabel());
        for (int i = 0; i < cuts.size(); i++) {
            moneyCell(row, i + 1, strong ? st.strongMoney() : st.money(), value.apply(cuts.get(i)));
        }
        moneyCell(row, cuts.size() + 1, strong ? st.strongMoney() : st.moneyBold(), total);
        return r + 1;
    }

    private BigDecimal sum(Collection<CashCut> cuts, Function<CashCut, BigDecimal> value) {
        return cuts.stream().map(value).map(this::nz).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void countedCell(Row row, int col, CellStyle style, BigDecimal amount) {
        if (amount != null) moneyCell(row, col, style, amount);
        else row.createCell(col).setCellValue("No contado");
    }

    private String registerName(CashCut cut) {
        return cut.getCashRegister() != null ? cut.getCashRegister().getName() : "—";
    }

    /** El día del reporte, o el rango si sus turnos abarcan más de uno. */
    private String reportDay(List<CashCut> cuts) {
        LocalDate from = cuts.get(0).getOpenedAt().toLocalDate();
        LocalDateTime closed = cuts.get(cuts.size() - 1).getClosedAt();
        LocalDate to = closed != null ? closed.toLocalDate() : from;
        return from.equals(to) ? from.format(DAY) : from.format(DAY) + " a " + to.format(DAY);
    }

    /** Horario del turno; con fechas si no abrió y cerró el mismo día. */
    private String turnHours(CashCut cut) {
        LocalDateTime opened = cut.getOpenedAt(), closed = cut.getClosedAt();
        if (closed == null) return opened.format(FMT);
        return opened.toLocalDate().equals(closed.toLocalDate())
                ? opened.format(HOUR) + " a " + closed.format(HOUR)
                : opened.format(FMT) + " a " + closed.format(FMT);
    }

    /**
     * Hoja "Cobros": mini-resumen por método (cobros y devoluciones) con TOTAL / DEVUELTO / NETO,
     * y el detalle de cada cobro agrupado por cajero. Las devoluciones van en la misma tabla, en
     * negativo y con el cobro del que salieron.
     */
    private void buildCobros(Sheet sheet, CellStyle header, CellStyle bold, CellStyle money,
                             Map<User, List<Pago>> pagosByCajero) {
        int r = 0;
        Map<PaymentMethod, long[]> counts = new EnumMap<>(PaymentMethod.class);
        Map<PaymentMethod, BigDecimal> totals = new EnumMap<>(PaymentMethod.class);
        Map<PaymentMethod, long[]> refundCounts = new EnumMap<>(PaymentMethod.class);
        Map<PaymentMethod, BigDecimal> refundTotals = new EnumMap<>(PaymentMethod.class);
        for (PaymentMethod pm : PaymentMethod.values()) {
            counts.put(pm, new long[]{0});
            totals.put(pm, BigDecimal.ZERO);
            refundCounts.put(pm, new long[]{0});
            refundTotals.put(pm, BigDecimal.ZERO);
        }
        BigDecimal grandTotal = BigDecimal.ZERO, grandRefunded = BigDecimal.ZERO;
        for (List<Pago> pagos : pagosByCajero.values()) {
            for (Pago p : pagos) {
                if (p.getMonto().signum() < 0) {
                    refundCounts.get(p.getMetodo())[0]++;
                    refundTotals.put(p.getMetodo(), refundTotals.get(p.getMetodo()).add(p.getMonto().negate()));
                    grandRefunded = grandRefunded.add(p.getMonto().negate());
                } else {
                    counts.get(p.getMetodo())[0]++;
                    totals.put(p.getMetodo(), totals.get(p.getMetodo()).add(p.getMonto()));
                    grandTotal = grandTotal.add(p.getMonto());
                }
            }
        }
        r += 2;
        for (PaymentMethod pm : PaymentMethod.values()) {
            Row row = sheet.createRow(r++);
            row.createCell(2).setCellValue(spanishMethod(pm));
            row.createCell(3).setCellValue(counts.get(pm)[0]);
            moneyCell(row, 4, money, totals.get(pm));
        }
        for (PaymentMethod pm : PaymentMethod.values()) {
            Row row = sheet.createRow(r++);
            row.createCell(2).setCellValue("DEVOLUCIONES EN " + spanishMethod(pm).replace("TRANSFERENCIAS", "TRANSFERENCIA"));
            row.createCell(3).setCellValue(refundCounts.get(pm)[0]);
            moneyCell(row, 4, money, refundTotals.get(pm));
        }
        Row totalRow = sheet.createRow(r++);
        totalRow.createCell(2).setCellValue("TOTAL");
        cellStyle(totalRow, 2, bold);
        moneyCell(totalRow, 4, money, grandTotal);
        Row refundedRow = sheet.createRow(r++);
        refundedRow.createCell(2).setCellValue("DEVUELTO");
        moneyCell(refundedRow, 4, money, grandRefunded.negate());
        Row netRow = sheet.createRow(r++);
        netRow.createCell(2).setCellValue("NETO");
        cellStyle(netRow, 2, bold);
        moneyCell(netRow, 4, money, grandTotal.subtract(grandRefunded));
        r += 2;

        header(sheet, r++, header, "ID_COBRO", "CUENTA", "CONCEPTO", "MESA / TIPO", "FECHA", "METODO", "COBRÓ", "MONTO");
        for (Map.Entry<User, List<Pago>> entry : pagosByCajero.entrySet()) {
            for (Pago p : entry.getValue()) {
                boolean refund = p.getMonto().signum() < 0;
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(p.getId());
                row.createCell(1).setCellValue(folio(p.getCuenta()));
                row.createCell(2).setCellValue(refund
                        ? "Devolución" + (p.getPagoOrigenId() != null ? " del cobro #" + p.getPagoOrigenId() : "")
                        : "Cobro");
                row.createCell(3).setCellValue(mesaOTipo(p.getCuenta().getOrden()));
                row.createCell(4).setCellValue(p.getCreatedAt() != null ? p.getCreatedAt().format(FMT) : "—");
                row.createCell(5).setCellValue(spanishMethod(p.getMetodo()));
                row.createCell(6).setCellValue(p.getUsuario() != null ? p.getUsuario().getName() : entry.getKey().getName());
                moneyCell(row, 7, money, p.getMonto());
            }
        }
        autosize(sheet, 8);
    }

    /**
     * Hoja "Productos": cada platillo de las cuentas cobradas en el reporte (una vez por cuenta,
     * aunque se haya pagado en varias partes), con categoría, descuento y subtotal. Al final, las
     * propinas de esas cuentas, que no son platillos pero sí se cobraron.
     */
    private void buildProductos(Sheet sheet, CellStyle header, CellStyle bold, CellStyle money,
                                Tienda tienda, Map<User, List<Pago>> pagosByCajero) {
        int r = 0;
        header(sheet, r++, header, "CUENTA", "FECHA", "MESA / TIPO", "PRODUCTO", "CATEGORIA", "CANTIDAD", "COBRÓ", "TIENDA", "DESCUENTO", "SUBTOTAL");

        Set<Long> vistas = new HashSet<>();
        BigDecimal grandTotal = BigDecimal.ZERO, grandDiscount = BigDecimal.ZERO, propinas = BigDecimal.ZERO;
        for (Map.Entry<User, List<Pago>> entry : pagosByCajero.entrySet()) {
            for (Pago p : entry.getValue()) {
                if (p.getMonto().signum() < 0) continue; // una devolución no repite los platillos
                Cuenta cuenta = p.getCuenta();
                if (!vistas.add(cuenta.getId())) continue;
                propinas = propinas.add(nz(cuenta.getPropina()));
                for (CuentaDetalle item : cuenta.getItems()) {
                    OrdenDetalle od = item.getOrdenDetalle();
                    Row row = sheet.createRow(r++);
                    row.createCell(0).setCellValue(folio(cuenta));
                    row.createCell(1).setCellValue(cuenta.getCreatedAt() != null ? cuenta.getCreatedAt().format(FMT) : "—");
                    row.createCell(2).setCellValue(mesaOTipo(cuenta.getOrden()));
                    row.createCell(3).setCellValue(od.getProductoNombre());
                    row.createCell(4).setCellValue(od.getProducto() != null && od.getProducto().getCategory() != null
                            ? od.getProducto().getCategory().getName() : "—");
                    row.createCell(5).setCellValue(item.getCantidad().doubleValue());
                    row.createCell(6).setCellValue(entry.getKey().getName());
                    row.createCell(7).setCellValue(tienda.getName());
                    BigDecimal descuento = descuentoProporcional(od, item.getCantidad());
                    moneyCell(row, 8, money, descuento);
                    moneyCell(row, 9, money, item.getMonto());
                    grandTotal = grandTotal.add(nz(item.getMonto()));
                    grandDiscount = grandDiscount.add(descuento);
                }
            }
        }
        Row totalRow = sheet.createRow(r++);
        totalRow.createCell(0).setCellValue("TOTAL");
        cellStyle(totalRow, 0, bold);
        moneyCell(totalRow, 8, money, grandDiscount);
        moneyCell(totalRow, 9, money, grandTotal);
        if (propinas.signum() > 0) {
            Row propinaRow = sheet.createRow(r);
            propinaRow.createCell(0).setCellValue("PROPINAS");
            cellStyle(propinaRow, 0, bold);
            moneyCell(propinaRow, 9, money, propinas);
        }
        autosize(sheet, 10);
    }

    /** Descuento de la línea de la orden que le toca a lo cobrado en esta cuenta (si se dividió). */
    private BigDecimal descuentoProporcional(OrdenDetalle od, BigDecimal cantidad) {
        BigDecimal desc = nz(od.getDescuento());
        if (desc.signum() == 0 || od.getCantidad() == null || od.getCantidad().signum() == 0 || cantidad == null) return desc;
        if (cantidad.compareTo(od.getCantidad()) >= 0) return desc;
        return desc.multiply(cantidad).divide(od.getCantidad(), 2, java.math.RoundingMode.HALF_UP);
    }

    private String folio(Cuenta cuenta) {
        return cuenta.getTicketFolio() != null ? cuenta.getTicketFolio() : "C-" + cuenta.getId();
    }

    /** "Mesa 4" / "Mesa Temporal 1", o el tipo de entrega (para llevar, domicilio...). */
    private String mesaOTipo(Orden orden) {
        if (orden == null) return "—";
        if (orden.getMesa() != null) return "Mesa " + orden.getMesa().getNumero();
        String tipo = OrdenService.etiquetaTipoEntrega(orden.getTipoEntrega());
        return tipo != null ? tipo : "—";
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

    /** Estilos de la hoja Resumen. */
    private record ResumenStyles(CellStyle title, CellStyle label, CellStyle headLeft, CellStyle headRight, CellStyle section,
                                 CellStyle money, CellStyle moneyBold, CellStyle strongLabel, CellStyle strongMoney, CellStyle wrap) {
    }

    private ResumenStyles resumenStyles(XSSFWorkbook wb) {
        short format = wb.createDataFormat().getFormat("$#,##0.00;[Red]-$#,##0.00");
        Font normalBold = wb.createFont();
        normalBold.setBold(true);
        Font big = wb.createFont();
        big.setBold(true);
        big.setFontHeightInPoints((short) 14);
        Font white = wb.createFont();
        white.setBold(true);
        white.setColor(IndexedColors.WHITE.getIndex());

        CellStyle title = wb.createCellStyle();
        title.setFont(big);
        CellStyle label = wb.createCellStyle();
        label.setFont(normalBold);

        XSSFCellStyle headLeft = filled(wb, 0x37, 0x41, 0x51);
        headLeft.setFont(white);
        headLeft.setVerticalAlignment(VerticalAlignment.CENTER);
        headLeft.setWrapText(true);
        XSSFCellStyle headRight = filled(wb, 0x37, 0x41, 0x51);
        headRight.setFont(white);
        headRight.setVerticalAlignment(VerticalAlignment.CENTER);
        headRight.setAlignment(HorizontalAlignment.RIGHT);
        headRight.setWrapText(true);

        XSSFCellStyle section = filled(wb, 0xE5, 0xE7, 0xEB);
        section.setFont(normalBold);

        CellStyle money = wb.createCellStyle();
        money.setDataFormat(format);
        CellStyle moneyBold = wb.createCellStyle();
        moneyBold.setDataFormat(format);
        moneyBold.setFont(normalBold);

        XSSFCellStyle strongLabel = filled(wb, 0xF3, 0xF4, 0xF6);
        strongLabel.setFont(normalBold);
        strongLabel.setBorderTop(BorderStyle.THIN);
        XSSFCellStyle strongMoney = filled(wb, 0xF3, 0xF4, 0xF6);
        strongMoney.setFont(normalBold);
        strongMoney.setBorderTop(BorderStyle.THIN);
        strongMoney.setDataFormat(format);
        CellStyle wrap = wb.createCellStyle();
        wrap.setWrapText(true);
        wrap.setVerticalAlignment(VerticalAlignment.TOP);
        return new ResumenStyles(title, label, headLeft, headRight, section, money, moneyBold, strongLabel, strongMoney, wrap);
    }

    private XSSFCellStyle filled(XSSFWorkbook wb, int red, int green, int blue) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(new XSSFColor(new byte[]{(byte) red, (byte) green, (byte) blue}, null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return s;
    }
}
