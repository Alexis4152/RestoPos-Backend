package com.nexora.restopos.service;

import com.nexora.restopos.dto.catalog.ReportCatalogDtos.Chart;
import com.nexora.restopos.dto.catalog.ReportCatalogDtos.Column;
import com.nexora.restopos.dto.catalog.ReportCatalogDtos.Filters;
import com.nexora.restopos.dto.catalog.ReportCatalogDtos.Metric;
import com.nexora.restopos.dto.catalog.ReportCatalogDtos.Point;
import com.nexora.restopos.dto.catalog.ReportCatalogDtos.Report;
import com.nexora.restopos.dto.catalog.ReportCatalogDtos.ReportInfo;
import com.nexora.restopos.model.CashCut;
import com.nexora.restopos.model.CashCutStatus;
import com.nexora.restopos.model.Product;
import com.nexora.restopos.model.User;
import com.nexora.restopos.repository.CashCutRepository;
import com.nexora.restopos.repository.ProductRepository;
import com.nexora.restopos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Catálogo de reportes del restaurante: un solo punto de entrada ({@link #catalog()} +
 * {@link #report(String, Filters, boolean, User)}) que arma cualquiera de los reportes en la
 * misma forma ({@link Report}: indicadores + gráficas + tabla), consumida por la pantalla de
 * "Reportes" y por su exportación a CSV / Excel / PDF ({@link ReportCatalogExporter}).
 *
 * <p>Mismo patrón que el módulo de reportes de DemoPV, con reportes propios de un restaurante
 * (cuentas cobradas, platillos, tipo de servicio, horas pico, meseros, cancelaciones,
 * reservaciones…). Las consultas agregadas van en SQL directo (aquí mismo) en vez de una
 * consulta suelta por reporte en cada repositorio.</p>
 *
 * <p>Criterios de fecha: lo cobrado (ingresos, métodos de pago, comparativo) cuenta por la
 * fecha del pago; las cuentas y meseros, por la fecha en que se cerró la cuenta; los
 * platillos, categorías y horas, por las órdenes ya pagadas (fecha de cierre de la orden).</p>
 *
 * <p>Los indicadores se calculan con una consulta agregada sobre TODO el periodo, no sobre las
 * filas traídas: si la tabla trae menos filas que el total ({@link #SCREEN_ROWS} en pantalla,
 * {@link #EXPORT_ROWS} al exportar), se marca {@code truncated} con una nota, pero las cifras
 * de arriba siguen siendo las reales.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportCatalogService {

    private static final int SCREEN_ROWS = 300;
    private static final int EXPORT_ROWS = 5000;
    private static final String CURRENCY = "MXN";
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private static final Map<String, String> METHOD_LABEL = Map.of(
            "CASH", "Efectivo", "CARD", "Tarjeta", "TRANSFER", "Transferencia");
    private static final Map<String, String> SERVICIO_LABEL = Map.of(
            "MESA", "Mesa", "PARA_LLEVAR", "Para llevar", "COMER_AQUI", "Comer aquí",
            "RECOGER", "Para recoger", "DOMICILIO", "A domicilio");
    private static final Map<String, String> MOVEMENT_LABEL = Map.of(
            "IN", "Entrada", "OUT", "Salida", "ADJUSTMENT", "Ajuste", "SALE", "Venta (salida)");
    private static final Map<String, String> RESERVA_LABEL = Map.of(
            "PENDIENTE", "Por confirmar", "CONFIRMADA", "Confirmada", "RECHAZADA", "Rechazada",
            "CANCELADA", "Cancelada", "LLEGO", "Llegó", "NO_LLEGO", "No llegó");
    private static final Map<CashCutStatus, String> CASHCUT_STATUS_LABEL = Map.of(
            CashCutStatus.OPEN, "Abierto", CashCutStatus.CLOSED, "Cerrado");
    private static final String[] DIAS = {"Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"};

    /** Catálogo: tipo → (título, descripción, grupo, ¿es foto actual?, filtros que acepta). */
    private static final List<ReportInfo> CATALOG = List.of(
            new ReportInfo("cuentas", "Cuentas cobradas", "Cada cuenta pagada del periodo: servicio, mesero, métodos de pago y propina.",
                    "Ventas", false, List.of("servicio", "method")),
            new ReportInfo("top-platillos", "Platillos más vendidos", "Cantidad e importe vendido de cada platillo (órdenes pagadas).",
                    "Ventas", false, List.of()),
            new ReportInfo("ventas-categoria", "Ventas por categoría", "Importe vendido agrupado por categoría del menú.",
                    "Ventas", false, List.of()),
            new ReportInfo("ventas-servicio", "Ventas por tipo de servicio", "Mesa, para llevar, comer aquí, para recoger y a domicilio (POS y página web).",
                    "Ventas", false, List.of()),
            new ReportInfo("horas-pico", "Horas y días de más venta", "Órdenes, comensales e importe por hora del día y por día de la semana.",
                    "Ventas", false, List.of()),
            new ReportInfo("meseros", "Desempeño de meseros", "Órdenes, consumo, propinas y calificación de cada mesero.",
                    "Personal", false, List.of()),
            new ReportInfo("cancelaciones", "Cancelaciones", "Órdenes canceladas con su motivo y quién las canceló.",
                    "Operación", false, List.of()),
            new ReportInfo("reservaciones", "Reservaciones", "Reservaciones por fecha, con personas, mesa y cómo terminaron.",
                    "Operación", false, List.of("status")),
            new ReportInfo("movimientos-inventario", "Movimientos de inventario", "Entradas, salidas, ventas y ajustes de stock.",
                    "Operación", false, List.of("movementType")),
            new ReportInfo("stock-bajo", "Stock bajo", "Productos con inventario igual o menor a su mínimo configurado.",
                    "Operación", true, List.of()),
            new ReportInfo("ingresos", "Ingresos por día", "Total cobrado día por día (incluye propinas).",
                    "Finanzas", false, List.of()),
            new ReportInfo("pagos-metodo", "Pagos por método", "Total cobrado por efectivo, tarjeta y transferencia — útil para cuadrar caja.",
                    "Finanzas", false, List.of()),
            new ReportInfo("rentabilidad", "Rentabilidad de platillos", "Importe vendido menos costo estimado de cada platillo.",
                    "Finanzas", false, List.of()),
            new ReportInfo("comparativo-anual", "Comparativo anual", "El periodo elegido contra el mismo rango del año anterior.",
                    "Finanzas", false, List.of()),
            new ReportInfo("cortes-caja", "Cortes de caja", "Historial de cortes de caja, abiertos y cerrados.",
                    "Finanzas", false, List.of("status")));

    // Tipo de servicio de una orden (mismas reglas que Orden.tipoEntrega + mesa): las preórdenes de
    // reservación cuentan como mesa; una orden sin mesa ni tipo es para llevar.
    private static final String SERVICIO_SQL = "(CASE WHEN o.tipo_entrega = 'DOMICILIO' THEN 'DOMICILIO' "
            + "WHEN o.tipo_entrega = 'RECOGER' THEN 'RECOGER' WHEN o.tipo_entrega = 'COMER_AQUI' THEN 'COMER_AQUI' "
            + "WHEN o.tipo_entrega = 'PARA_LLEVAR' THEN 'PARA_LLEVAR' "
            + "WHEN o.mesa_id IS NOT NULL OR o.tipo_entrega = 'RESERVACION' THEN 'MESA' ELSE 'PARA_LLEVAR' END)";
    private static final String TIENDA = " AND (CAST(:t AS bigint) IS NULL OR o.tienda_id = :t)";
    // Platillos de órdenes ya pagadas en el periodo (sin cancelados ni el cargo de envío).
    private static final String PLATILLOS_PAGADOS = " FROM orden_detalles d JOIN ordenes o ON o.id = d.orden_id "
            + "WHERE o.estado = 'PAGADA' AND COALESCE(o.closed_at, o.opened_at) >= :start AND COALESCE(o.closed_at, o.opened_at) < :end "
            + "AND d.estado <> 'CANCELADO' AND d.cargo_extra = false" + TIENDA;
    private static final String PAGOS = " FROM pagos p JOIN cuentas c ON c.id = p.cuenta_id JOIN ordenes o ON o.id = c.orden_id "
            + "WHERE p.cancelado = false AND p.created_at >= :start AND p.created_at < :end" + TIENDA;

    private final NamedParameterJdbcTemplate jdbc;
    private final CashCutRepository cashCutRepository;
    private final ProductRepository productRepository;
    private final TenantScope tenantScope;

    public List<ReportInfo> catalog() {
        return CATALOG;
    }

    private static ReportInfo info(String type) {
        return CATALOG.stream().filter(i -> i.type().equals(type)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Reporte no encontrado: " + type));
    }

    /** Contexto de una consulta: tienda del actor, rango [start, end) y cuántas filas traer. */
    private record Ctx(Long tiendaId, LocalDate from, LocalDate to, LocalDateTime start, LocalDateTime end, int limit) {
        MapSqlParameterSource params() {
            return new MapSqlParameterSource()
                    .addValue("t", tiendaId, Types.BIGINT)
                    .addValue("start", start)
                    .addValue("end", end)
                    .addValue("limit", limit);
        }
    }

    public Report report(String type, Filters raw, boolean forExport, User actor) {
        ReportInfo meta = info(type);
        LocalDate today = LocalDate.now();
        LocalDate from = raw.from() != null ? raw.from() : today.withDayOfMonth(1);
        LocalDate to = raw.to() != null ? raw.to() : today;
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("La fecha final no puede ser anterior a la inicial");
        }
        Ctx c = new Ctx(tenantScope.scopeId(actor), from, to, from.atStartOfDay(), to.plusDays(1).atStartOfDay(),
                forExport ? EXPORT_ROWS : SCREEN_ROWS);
        Report r = switch (type) {
            case "cuentas" -> cuentas(c, raw);
            case "top-platillos" -> topPlatillos(c);
            case "ventas-categoria" -> ventasPorCategoria(c);
            case "ventas-servicio" -> ventasPorServicio(c);
            case "horas-pico" -> horasPico(c);
            case "meseros" -> meseros(c);
            case "cancelaciones" -> cancelaciones(c);
            case "reservaciones" -> reservaciones(c, raw);
            case "movimientos-inventario" -> movimientosInventario(c, raw);
            case "stock-bajo" -> stockBajo(c);
            case "ingresos" -> ingresos(c);
            case "pagos-metodo" -> pagosPorMetodo(c);
            case "rentabilidad" -> rentabilidad(c);
            case "comparativo-anual" -> comparativoAnual(c);
            case "cortes-caja" -> cortesCaja(c, raw);
            default -> throw new IllegalArgumentException("Reporte no encontrado: " + type);
        };
        return new Report(type, meta.title(), r.basis(), r.from(), r.to(), CURRENCY, Instant.now(),
                r.metrics(), r.charts(), r.columns(), r.rows(), r.totalRows(), r.truncated(), r.notes());
    }

    // ── Ventas ────────────────────────────────────────────────────────────────────────────

    private Report cuentas(Ctx c, Filters f) {
        String servicio = option(f.servicio(), SERVICIO_LABEL.keySet());
        String method = option(f.method(), METHOD_LABEL.keySet());
        MapSqlParameterSource p = c.params().addValue("svc", servicio, Types.VARCHAR).addValue("method", method, Types.VARCHAR);
        String where = " FROM cuentas c JOIN ordenes o ON o.id = c.orden_id "
                + "WHERE c.estado = 'PAGADA' AND c.closed_at >= :start AND c.closed_at < :end" + TIENDA
                + " AND (CAST(:svc AS varchar) IS NULL OR " + SERVICIO_SQL + " = :svc)"
                + " AND (CAST(:method AS varchar) IS NULL OR EXISTS (SELECT 1 FROM pagos px WHERE px.cuenta_id = c.id "
                + "AND px.cancelado = false AND px.metodo = :method))";

        List<Map<String, Object>> rows = jdbc.query("SELECT c.id, c.ticket_folio, c.closed_at, o.id AS orden, " + SERVICIO_SQL
                + " AS svc, o.origen, m.numero AS mesa, u.name AS mesero, c.total, c.propina, "
                + "(SELECT string_agg(DISTINCT px.metodo, ',') FROM pagos px WHERE px.cuenta_id = c.id AND px.cancelado = false) AS metodos"
                + where.replaceFirst(" WHERE ", " LEFT JOIN mesas m ON m.id = o.mesa_id LEFT JOIN users u ON u.id = o.mesero_id WHERE ")
                + " ORDER BY c.closed_at DESC LIMIT :limit", p, (rs, i) -> {
            BigDecimal total = nz(rs.getBigDecimal("total"));
            BigDecimal propina = nz(rs.getBigDecimal("propina"));
            return row(
                    "folio", rs.getString("ticket_folio") != null ? rs.getString("ticket_folio") : "#" + rs.getLong("id"),
                    "fecha", iso(rs, "closed_at"),
                    "orden", rs.getLong("orden"),
                    "servicio", servicio(rs),
                    "mesero", dash(rs.getString("mesero")),
                    "metodos", metodos(rs.getString("metodos")),
                    "consumo", total.subtract(propina),
                    "propina", propina,
                    "total", total);
        });
        Map<String, Object> agg = jdbc.queryForMap("SELECT COUNT(*) AS n, COALESCE(SUM(c.total), 0) AS total, "
                + "COALESCE(SUM(c.propina), 0) AS propina" + where, p);
        long n = ((Number) agg.get("n")).longValue();
        BigDecimal total = dec(agg.get("total"));
        BigDecimal propina = dec(agg.get("propina"));
        List<Column> columns = List.of(
                new Column("folio", "Folio", "text"),
                new Column("fecha", "Cobrada", "datetime"),
                new Column("orden", "Orden", "number"),
                new Column("servicio", "Servicio", "text"),
                new Column("mesero", "Mesero", "text"),
                new Column("metodos", "Pagó con", "text"),
                new Column("consumo", "Consumo", "money"),
                new Column("propina", "Propina", "money"),
                new Column("total", "Total", "money"));
        List<Metric> metrics = List.of(
                metric("count", "Cuentas cobradas", n, "count", null),
                metric("consumo", "Consumo", total.subtract(propina), "money", "Lo que se vendió, sin propinas."),
                metric("propina", "Propinas", propina, "money", null),
                metric("avg", "Ticket promedio", n > 0 ? total.subtract(propina).divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO, "money", "Consumo promedio por cuenta."));
        return base(c, columns, rows, (int) n, metrics, List.of());
    }

    private Report topPlatillos(Ctx c) {
        List<Point> points = new ArrayList<>();
        List<Map<String, Object>> rows = jdbc.query("SELECT d.producto_nombre AS nombre, SUM(d.cantidad) AS cantidad, "
                + "COALESCE(SUM(d.subtotal), 0) AS total" + PLATILLOS_PAGADOS
                + " GROUP BY d.producto_nombre ORDER BY cantidad DESC, total DESC LIMIT :limit", c.params(), (rs, i) -> {
            String name = rs.getString("nombre");
            if (i < 15) points.add(new Point(truncate(name, 20), List.of(rs.getBigDecimal("cantidad"))));
            return row("platillo", name, "cantidad", rs.getBigDecimal("cantidad"), "total", nz(rs.getBigDecimal("total")));
        });
        Map<String, Object> agg = jdbc.queryForMap("SELECT COUNT(DISTINCT d.producto_nombre) AS n, COALESCE(SUM(d.cantidad), 0) AS piezas, "
                + "COALESCE(SUM(d.subtotal), 0) AS total" + PLATILLOS_PAGADOS, c.params());
        List<Column> columns = List.of(
                new Column("platillo", "Platillo", "text"),
                new Column("cantidad", "Cantidad vendida", "number"),
                new Column("total", "Importe", "money"));
        List<Metric> metrics = List.of(
                metric("piezas", "Platillos vendidos", agg.get("piezas"), "number", null),
                metric("total", "Importe vendido", dec(agg.get("total")), "money", "Sin propinas ni cargos de envío."));
        Chart chart = new Chart("top", "Platillos más vendidos (piezas)", "bar", "number", List.of("Piezas"), points);
        return base(c, columns, rows, ((Number) agg.get("n")).intValue(), metrics, rows.isEmpty() ? List.of() : List.of(chart));
    }

    private Report ventasPorCategoria(Ctx c) {
        List<Point> points = new ArrayList<>();
        BigDecimal[] sum = {BigDecimal.ZERO};
        List<Map<String, Object>> rows = jdbc.query("SELECT COALESCE(cat.name, 'Sin categoría') AS categoria, SUM(d.cantidad) AS cantidad, "
                + "COALESCE(SUM(d.subtotal), 0) AS total"
                + PLATILLOS_PAGADOS.replaceFirst(" WHERE ", " LEFT JOIN products pr ON pr.id = d.producto_id LEFT JOIN categories cat ON cat.id = pr.category_id WHERE ")
                + " GROUP BY 1 ORDER BY total DESC", c.params(), (rs, i) -> {
            BigDecimal total = nz(rs.getBigDecimal("total"));
            sum[0] = sum[0].add(total);
            points.add(new Point(truncate(rs.getString("categoria"), 20), List.of(total)));
            return row("categoria", rs.getString("categoria"), "cantidad", rs.getBigDecimal("cantidad"), "total", total);
        });
        List<Column> columns = List.of(
                new Column("categoria", "Categoría", "text"),
                new Column("cantidad", "Platillos vendidos", "number"),
                new Column("total", "Importe", "money"));
        List<Metric> metrics = List.of(metric("total", "Importe vendido", sum[0], "money", "Sin propinas ni cargos de envío."));
        Chart chart = new Chart("cat", "Ventas por categoría", "bar", "money", List.of("Importe"), points);
        return base(c, columns, rows, rows.size(), metrics, rows.isEmpty() ? List.of() : List.of(chart));
    }

    private Report ventasPorServicio(Ctx c) {
        List<Point> points = new ArrayList<>();
        BigDecimal[] sum = {BigDecimal.ZERO};
        long[] ordenes = {0};
        List<Map<String, Object>> rows = jdbc.query("SELECT " + SERVICIO_SQL + " AS svc, (o.origen = 'WEB') AS web, "
                + "COUNT(DISTINCT o.id) AS ordenes, COALESCE(SUM(c.total - COALESCE(c.propina, 0)), 0) AS consumo, COALESCE(SUM(c.propina), 0) AS propina "
                + "FROM cuentas c JOIN ordenes o ON o.id = c.orden_id "
                + "WHERE c.estado = 'PAGADA' AND c.closed_at >= :start AND c.closed_at < :end" + TIENDA
                + " GROUP BY 1, 2 ORDER BY consumo DESC", c.params(), (rs, i) -> {
            String label = SERVICIO_LABEL.getOrDefault(rs.getString("svc"), rs.getString("svc")) + (rs.getBoolean("web") ? " (web)" : "");
            long n = rs.getLong("ordenes");
            BigDecimal consumo = nz(rs.getBigDecimal("consumo"));
            sum[0] = sum[0].add(consumo);
            ordenes[0] += n;
            points.add(new Point(label, List.of(consumo)));
            return row("servicio", label, "ordenes", n, "consumo", consumo, "propina", nz(rs.getBigDecimal("propina")),
                    "promedio", n > 0 ? consumo.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        });
        List<Column> columns = List.of(
                new Column("servicio", "Servicio", "text"),
                new Column("ordenes", "Órdenes", "count"),
                new Column("consumo", "Consumo", "money"),
                new Column("propina", "Propinas", "money"),
                new Column("promedio", "Promedio por orden", "money"));
        List<Metric> metrics = List.of(
                metric("ordenes", "Órdenes cobradas", ordenes[0], "count", null),
                metric("consumo", "Consumo", sum[0], "money", "Sin propinas."));
        Chart chart = new Chart("svc", "Consumo por tipo de servicio", "bar", "money", List.of("Consumo"), points);
        return base(c, columns, rows, rows.size(), metrics, rows.isEmpty() ? List.of() : List.of(chart));
    }

    private Report horasPico(Ctx c) {
        String pagadas = " FROM ordenes o WHERE o.estado = 'PAGADA' AND COALESCE(o.closed_at, o.opened_at) >= :start "
                + "AND COALESCE(o.closed_at, o.opened_at) < :end" + TIENDA;
        List<Point> horas = new ArrayList<>();
        String[] pico = {null};
        BigDecimal[] max = {BigDecimal.ZERO};
        List<Map<String, Object>> rows = jdbc.query("SELECT EXTRACT(HOUR FROM o.opened_at)::int AS hora, COUNT(*) AS ordenes, "
                + "COALESCE(SUM(o.numero_comensales), 0) AS comensales, COALESCE(SUM(o.total), 0) AS total" + pagadas
                + " GROUP BY 1 ORDER BY 1", c.params(), (rs, i) -> {
            String label = String.format("%02d:00", rs.getInt("hora"));
            BigDecimal total = nz(rs.getBigDecimal("total"));
            if (total.compareTo(max[0]) > 0) { max[0] = total; pico[0] = label; }
            horas.add(new Point(label, List.of(total)));
            return row("hora", label + " – " + String.format("%02d:59", rs.getInt("hora")), "ordenes", rs.getLong("ordenes"),
                    "comensales", rs.getLong("comensales"), "total", total);
        });
        List<Point> dias = new ArrayList<>();
        jdbc.query("SELECT EXTRACT(ISODOW FROM o.opened_at)::int AS dia, COALESCE(SUM(o.total), 0) AS total" + pagadas
                + " GROUP BY 1 ORDER BY 1", c.params(), (ResultSet rs) -> {
            dias.add(new Point(DIAS[rs.getInt("dia") - 1], List.of(nz(rs.getBigDecimal("total")))));
        });
        List<Column> columns = List.of(
                new Column("hora", "Hora en que se abrió la orden", "text"),
                new Column("ordenes", "Órdenes", "count"),
                new Column("comensales", "Comensales", "count"),
                new Column("total", "Consumo", "money"));
        List<Metric> metrics = List.of(metric("pico", "Hora de más venta", pico[0] != null ? pico[0] : "—", "text", null));
        List<Chart> charts = rows.isEmpty() ? List.of() : List.of(
                new Chart("horas", "Consumo por hora", "bar", "money", List.of("Consumo"), horas),
                new Chart("dias", "Consumo por día de la semana", "bar", "money", List.of("Consumo"), dias));
        return base(c, columns, rows, rows.size(), metrics, charts);
    }

    // ── Personal ──────────────────────────────────────────────────────────────────────────

    private Report meseros(Ctx c) {
        List<Point> points = new ArrayList<>();
        BigDecimal[] propinas = {BigDecimal.ZERO};
        List<Map<String, Object>> rows = jdbc.query("SELECT u.id, COALESCE(u.name, 'Sin mesero') AS mesero, COUNT(DISTINCT o.id) AS ordenes, "
                + "COUNT(*) AS cuentas, COALESCE(SUM(c.total - COALESCE(c.propina, 0)), 0) AS consumo, COALESCE(SUM(c.propina), 0) AS propina, "
                + "(SELECT ROUND(AVG(op.mesero_estrellas), 1) FROM opiniones op WHERE op.mesero_id = u.id AND op.mesero_estrellas IS NOT NULL "
                + "AND op.created_at >= :start AND op.created_at < :end) AS calificacion "
                + "FROM cuentas c JOIN ordenes o ON o.id = c.orden_id LEFT JOIN users u ON u.id = o.mesero_id "
                + "WHERE c.estado = 'PAGADA' AND c.closed_at >= :start AND c.closed_at < :end" + TIENDA
                + " GROUP BY u.id, u.name ORDER BY consumo DESC LIMIT :limit", c.params(), (rs, i) -> {
            long ordenes = rs.getLong("ordenes");
            BigDecimal consumo = nz(rs.getBigDecimal("consumo"));
            BigDecimal propina = nz(rs.getBigDecimal("propina"));
            propinas[0] = propinas[0].add(propina);
            points.add(new Point(truncate(rs.getString("mesero"), 18), List.of(consumo)));
            return row("mesero", rs.getString("mesero"), "ordenes", ordenes, "cuentas", rs.getLong("cuentas"),
                    "consumo", consumo, "propina", propina,
                    "promedio", ordenes > 0 ? consumo.divide(BigDecimal.valueOf(ordenes), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO,
                    "calificacion", rs.getBigDecimal("calificacion"));
        });
        List<Column> columns = List.of(
                new Column("mesero", "Mesero", "text"),
                new Column("ordenes", "Órdenes", "count"),
                new Column("cuentas", "Cuentas", "count"),
                new Column("consumo", "Consumo", "money"),
                new Column("propina", "Propinas", "money"),
                new Column("promedio", "Promedio por orden", "money"),
                new Column("calificacion", "Calificación (1–5)", "number"));
        List<Metric> metrics = List.of(metric("propina", "Propinas del periodo", propinas[0], "money", null));
        Chart chart = new Chart("meseros", "Consumo por mesero", "bar", "money", List.of("Consumo"), points);
        List<String> notes = List.of("En para llevar y a domicilio, el \"mesero\" es quien abrió la orden en el POS. "
                + "La calificación sale de las opiniones de los clientes del periodo.");
        Report r = base(c, columns, rows, rows.size(), metrics, rows.isEmpty() ? List.of() : List.of(chart));
        return withNotes(r, notes);
    }

    // ── Operación ─────────────────────────────────────────────────────────────────────────

    private Report cancelaciones(Ctx c) {
        String where = " FROM ordenes o WHERE o.estado = 'CANCELADA' AND o.cancelled_at >= :start AND o.cancelled_at < :end" + TIENDA;
        List<Map<String, Object>> rows = jdbc.query("SELECT o.id, o.cancelled_at, " + SERVICIO_SQL + " AS svc, o.origen, m.numero AS mesa, "
                + "u.name AS mesero, cu.name AS cancelo, o.motivo_cancelacion, o.total"
                + where.replaceFirst(" WHERE ", " LEFT JOIN mesas m ON m.id = o.mesa_id LEFT JOIN users u ON u.id = o.mesero_id "
                + "LEFT JOIN users cu ON cu.id = o.cancelled_by_user_id WHERE ")
                + " ORDER BY o.cancelled_at DESC LIMIT :limit", c.params(), (rs, i) -> row(
                "orden", rs.getLong("id"),
                "fecha", iso(rs, "cancelled_at"),
                "servicio", servicio(rs),
                "mesero", dash(rs.getString("mesero")),
                "cancelo", dash(rs.getString("cancelo")),
                "motivo", dash(rs.getString("motivo_cancelacion")),
                "total", nz(rs.getBigDecimal("total"))));
        Map<String, Object> agg = jdbc.queryForMap("SELECT COUNT(*) AS n, COALESCE(SUM(o.total), 0) AS total" + where, c.params());
        // Platillos cancelados sueltos dentro de órdenes que sí siguieron (ej. "ya no lo quiso").
        Map<String, Object> sueltos = jdbc.queryForMap("SELECT COUNT(*) AS n, COALESCE(SUM(d.subtotal), 0) AS total "
                + "FROM orden_detalles d JOIN ordenes o ON o.id = d.orden_id WHERE d.estado = 'CANCELADO' "
                + "AND o.estado <> 'CANCELADA' AND d.created_at >= :start AND d.created_at < :end" + TIENDA, c.params());
        List<Column> columns = List.of(
                new Column("orden", "Orden", "number"),
                new Column("fecha", "Cancelada", "datetime"),
                new Column("servicio", "Servicio", "text"),
                new Column("mesero", "Mesero", "text"),
                new Column("cancelo", "Canceló", "text"),
                new Column("motivo", "Motivo", "text"),
                new Column("total", "Importe", "money"));
        List<Metric> metrics = List.of(
                metric("ordenes", "Órdenes canceladas", agg.get("n"), "count", null),
                metric("importe", "Importe de órdenes canceladas", dec(agg.get("total")), "money", null),
                metric("platillos", "Platillos cancelados sueltos", sueltos.get("n"), "count",
                        "Cancelados dentro de órdenes que sí se cobraron (" + money(dec(sueltos.get("total"))) + ")."));
        return base(c, columns, rows, ((Number) agg.get("n")).intValue(), metrics, List.of());
    }

    private Report reservaciones(Ctx c, Filters f) {
        String estado = option(f.status(), RESERVA_LABEL.keySet());
        MapSqlParameterSource p = c.params().addValue("estado", estado, Types.VARCHAR);
        String where = " FROM reservaciones r WHERE r.fecha_hora >= :start AND r.fecha_hora < :end "
                + "AND (CAST(:t AS bigint) IS NULL OR r.tienda_id = :t) AND (CAST(:estado AS varchar) IS NULL OR r.estado = :estado)";
        List<Map<String, Object>> rows = jdbc.query("SELECT r.fecha_hora, r.nombre, r.telefono, r.personas, m.numero AS mesa, "
                + "z.nombre AS zona, r.estado, r.origen, r.ocasion"
                + where.replaceFirst(" WHERE ", " LEFT JOIN mesas m ON m.id = r.mesa_id LEFT JOIN zonas z ON z.id = r.zona_id WHERE ")
                + " ORDER BY r.fecha_hora DESC LIMIT :limit", p, (rs, i) -> row(
                "fecha", iso(rs, "fecha_hora"),
                "nombre", dash(rs.getString("nombre")),
                "telefono", dash(rs.getString("telefono")),
                "personas", rs.getInt("personas"),
                "mesa", rs.getString("mesa") != null ? "Mesa " + rs.getString("mesa") : dash(rs.getString("zona")),
                "estado", RESERVA_LABEL.getOrDefault(rs.getString("estado"), rs.getString("estado")),
                "origen", "WEB".equals(rs.getString("origen")) ? "Página web" : "Restaurante",
                "ocasion", dash(rs.getString("ocasion"))));
        Map<String, Object> agg = jdbc.queryForMap("SELECT COUNT(*) AS n, COALESCE(SUM(r.personas), 0) AS personas, "
                + "COUNT(*) FILTER (WHERE r.estado = 'LLEGO') AS llego, COUNT(*) FILTER (WHERE r.estado = 'NO_LLEGO') AS no_llego" + where, p);
        long llego = ((Number) agg.get("llego")).longValue();
        long noLlego = ((Number) agg.get("no_llego")).longValue();
        List<Column> columns = List.of(
                new Column("fecha", "Fecha y hora", "datetime"),
                new Column("nombre", "A nombre de", "text"),
                new Column("telefono", "Teléfono", "text"),
                new Column("personas", "Personas", "count"),
                new Column("mesa", "Mesa / zona", "text"),
                new Column("estado", "Estado", "text"),
                new Column("origen", "Origen", "text"),
                new Column("ocasion", "Ocasión", "text"));
        List<Metric> metrics = List.of(
                metric("count", "Reservaciones", agg.get("n"), "count", null),
                metric("personas", "Personas", agg.get("personas"), "count", null),
                metric("noshow", "No llegaron", llego + noLlego > 0
                        ? BigDecimal.valueOf(noLlego * 100.0 / (llego + noLlego)).setScale(1, RoundingMode.HALF_UP) : null,
                        "rate", llego + noLlego > 0 ? noLlego + " de " + (llego + noLlego) + " reservaciones ya atendidas." : "Aún no hay reservaciones atendidas."));
        return base(c, columns, rows, ((Number) agg.get("n")).intValue(), metrics, List.of());
    }

    private Report movimientosInventario(Ctx c, Filters f) {
        String tipo = option(f.movementType(), MOVEMENT_LABEL.keySet());
        MapSqlParameterSource p = c.params().addValue("tipo", tipo, Types.VARCHAR);
        String where = " FROM inventory_movements mv JOIN products pr ON pr.id = mv.product_id "
                + "WHERE mv.created_at >= :start AND mv.created_at < :end "
                + "AND (CAST(:t AS bigint) IS NULL OR pr.tienda_id = :t) AND (CAST(:tipo AS varchar) IS NULL OR mv.type = :tipo)";
        List<Map<String, Object>> rows = jdbc.query("SELECT mv.created_at, pr.name AS producto, mv.type, mv.quantity, mv.previous_stock, "
                + "mv.new_stock, u.name AS usuario, mv.reason"
                + where.replaceFirst(" WHERE ", " LEFT JOIN users u ON u.id = mv.user_id WHERE ")
                + " ORDER BY mv.created_at DESC LIMIT :limit", p, (rs, i) -> row(
                "fecha", iso(rs, "created_at"),
                "producto", rs.getString("producto"),
                "tipo", MOVEMENT_LABEL.getOrDefault(rs.getString("type"), rs.getString("type")),
                "cantidad", rs.getInt("quantity"),
                "stockAnterior", rs.getInt("previous_stock"),
                "stockNuevo", rs.getInt("new_stock"),
                "usuario", dash(rs.getString("usuario")),
                "motivo", dash(rs.getString("reason"))));
        long n = jdbc.queryForObject("SELECT COUNT(*)" + where, p, Long.class);
        List<Column> columns = List.of(
                new Column("fecha", "Fecha", "datetime"),
                new Column("producto", "Producto", "text"),
                new Column("tipo", "Tipo", "text"),
                new Column("cantidad", "Cantidad", "number"),
                new Column("stockAnterior", "Stock anterior", "number"),
                new Column("stockNuevo", "Stock nuevo", "number"),
                new Column("usuario", "Usuario", "text"),
                new Column("motivo", "Motivo", "text"));
        return base(c, columns, rows, (int) n, List.of(metric("count", "Movimientos", n, "count", null)), List.of());
    }

    private Report stockBajo(Ctx c) {
        List<Product> low = productRepository.findLowStockForTienda(c.tiendaId());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Product pr : low.subList(0, Math.min(low.size(), c.limit()))) {
            rows.add(row("producto", pr.getName(), "categoria", pr.getCategory() != null ? pr.getCategory().getName() : "—",
                    "stock", pr.getStock(), "minimo", pr.getMinStock(), "unidad", dash(pr.getUnit())));
        }
        List<Column> columns = List.of(
                new Column("producto", "Producto", "text"),
                new Column("categoria", "Categoría", "text"),
                new Column("stock", "Existencia", "number"),
                new Column("minimo", "Mínimo configurado", "number"),
                new Column("unidad", "Unidad", "text"));
        return base(c, columns, rows, low.size(), List.of(metric("count", "Productos en stock bajo", low.size(), "count", null)), List.of());
    }

    // ── Finanzas ──────────────────────────────────────────────────────────────────────────

    private Report ingresos(Ctx c) {
        List<Point> points = new ArrayList<>();
        BigDecimal[] sum = {BigDecimal.ZERO};
        long[] count = {0};
        List<Map<String, Object>> rows = jdbc.query("SELECT CAST(p.created_at AS date) AS dia, COALESCE(SUM(p.monto), 0) AS total, "
                + "COUNT(*) AS pagos" + PAGOS + " GROUP BY 1 ORDER BY 1", c.params(), (rs, i) -> {
            String dia = rs.getDate("dia").toLocalDate().toString();
            BigDecimal total = nz(rs.getBigDecimal("total"));
            sum[0] = sum[0].add(total);
            count[0] += rs.getLong("pagos");
            points.add(new Point(dia, List.of(total)));
            return row("fecha", dia, "total", total, "pagos", rs.getLong("pagos"));
        });
        long dias = rows.size();
        List<Column> columns = List.of(
                new Column("fecha", "Fecha", "date"),
                new Column("total", "Total cobrado", "money"),
                new Column("pagos", "Pagos", "count"));
        List<Metric> metrics = List.of(
                metric("total", "Total cobrado", sum[0], "money", "Incluye propinas."),
                metric("pagos", "Pagos registrados", count[0], "count", null),
                metric("avg", "Promedio por día con venta", dias > 0 ? sum[0].divide(BigDecimal.valueOf(dias), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO, "money", null));
        Chart chart = new Chart("income", "Ingresos por día", "line", "money", List.of("Total"), points);
        return base(c, columns, rows, rows.size(), metrics, rows.isEmpty() ? List.of() : List.of(chart));
    }

    private Report pagosPorMetodo(Ctx c) {
        List<Point> points = new ArrayList<>();
        BigDecimal[] sum = {BigDecimal.ZERO};
        List<Map<String, Object>> rows = jdbc.query("SELECT p.metodo, COALESCE(SUM(p.monto), 0) AS total, COUNT(*) AS pagos" + PAGOS
                + " GROUP BY p.metodo ORDER BY total DESC", c.params(), (rs, i) -> {
            String label = METHOD_LABEL.getOrDefault(rs.getString("metodo"), rs.getString("metodo"));
            BigDecimal total = nz(rs.getBigDecimal("total"));
            sum[0] = sum[0].add(total);
            points.add(new Point(label, List.of(total)));
            return row("metodo", label, "total", total, "pagos", rs.getLong("pagos"));
        });
        List<Column> columns = List.of(
                new Column("metodo", "Método", "text"),
                new Column("total", "Total cobrado", "money"),
                new Column("pagos", "Pagos", "count"));
        Chart chart = new Chart("method", "Pagos por método", "bar", "money", List.of("Total"), points);
        return base(c, columns, rows, rows.size(), List.of(metric("total", "Total cobrado", sum[0], "money", "Incluye propinas.")),
                rows.isEmpty() ? List.of() : List.of(chart));
    }

    private Report rentabilidad(Ctx c) {
        List<Point> points = new ArrayList<>();
        BigDecimal[] margenTotal = {BigDecimal.ZERO};
        int[] sinCosto = {0};
        List<Map<String, Object>> rows = jdbc.query("SELECT d.producto_nombre AS nombre, SUM(d.cantidad) AS cantidad, "
                + "COALESCE(SUM(d.subtotal), 0) AS ingreso, COALESCE(SUM(d.cantidad * COALESCE(pr.cost, 0)), 0) AS costo, "
                + "BOOL_OR(COALESCE(pr.cost, 0) = 0) AS sin_costo"
                + PLATILLOS_PAGADOS.replaceFirst(" WHERE ", " LEFT JOIN products pr ON pr.id = d.producto_id WHERE ")
                + " GROUP BY d.producto_nombre ORDER BY (COALESCE(SUM(d.subtotal), 0) - COALESCE(SUM(d.cantidad * COALESCE(pr.cost, 0)), 0)) DESC "
                + "LIMIT :limit", c.params(), (rs, i) -> {
            BigDecimal ingreso = nz(rs.getBigDecimal("ingreso"));
            BigDecimal costo = nz(rs.getBigDecimal("costo"));
            BigDecimal margen = ingreso.subtract(costo);
            margenTotal[0] = margenTotal[0].add(margen);
            if (rs.getBoolean("sin_costo")) sinCosto[0]++;
            if (i < 15) points.add(new Point(truncate(rs.getString("nombre"), 20), List.of(margen)));
            return row("platillo", rs.getString("nombre"), "cantidad", rs.getBigDecimal("cantidad"), "ingreso", ingreso,
                    "costo", costo, "margen", margen,
                    "margenPct", ingreso.signum() > 0 ? margen.multiply(BigDecimal.valueOf(100)).divide(ingreso, 1, RoundingMode.HALF_UP) : null);
        });
        List<Column> columns = List.of(
                new Column("platillo", "Platillo", "text"),
                new Column("cantidad", "Vendidos", "number"),
                new Column("ingreso", "Importe", "money"),
                new Column("costo", "Costo estimado", "money"),
                new Column("margen", "Margen", "money"),
                new Column("margenPct", "Margen %", "rate"));
        List<Metric> metrics = List.of(metric("margin", "Margen total", margenTotal[0], "money",
                "El costo es el ACTUAL de cada platillo en el menú, no el que tenía al momento de cada venta."));
        List<String> notes = new ArrayList<>(List.of("El costo es el costo ACTUAL de cada platillo en el menú (Menú → editar platillo), no el que tenía al momento de cada venta."));
        if (sinCosto[0] > 0) {
            notes.add(sinCosto[0] + " platillo(s) no tienen costo capturado: su margen aparece igual al importe vendido.");
        }
        Chart chart = new Chart("margin", "Margen por platillo", "bar", "money", List.of("Margen"), points);
        return withNotes(base(c, columns, rows, rows.size(), metrics, rows.isEmpty() ? List.of() : List.of(chart)), notes);
    }

    private Report comparativoAnual(Ctx c) {
        LocalDate prevFrom = c.from().minusYears(1);
        LocalDate prevTo = c.to().minusYears(1);
        Ctx prev = new Ctx(c.tiendaId(), prevFrom, prevTo, prevFrom.atStartOfDay(), prevTo.plusDays(1).atStartOfDay(), c.limit());

        String sql = "SELECT COALESCE(SUM(p.monto), 0) AS total, COUNT(DISTINCT c.id) AS cuentas" + PAGOS;
        Map<String, Object> cur = jdbc.queryForMap(sql, c.params());
        Map<String, Object> ant = jdbc.queryForMap(sql, prev.params());
        BigDecimal curTotal = dec(cur.get("total"));
        BigDecimal prevTotal = dec(ant.get("total"));
        BigDecimal change = prevTotal.signum() > 0
                ? curTotal.subtract(prevTotal).divide(prevTotal, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : null;

        List<Column> columns = List.of(
                new Column("periodo", "Periodo", "text"),
                new Column("rango", "Rango", "text"),
                new Column("total", "Total cobrado", "money"),
                new Column("cuentas", "Cuentas", "count"));
        List<Map<String, Object>> rows = List.of(
                row("periodo", "Periodo actual", "rango", c.from() + " – " + c.to(), "total", curTotal, "cuentas", cur.get("cuentas")),
                row("periodo", "Mismo rango, año anterior", "rango", prevFrom + " – " + prevTo, "total", prevTotal, "cuentas", ant.get("cuentas")));
        List<Metric> metrics = List.of(
                metric("current", "Periodo actual", curTotal, "money", null),
                metric("previous", "Año anterior", prevTotal, "money", null),
                metric("change", "Variación", change, "percent", change == null ? "El año anterior no tuvo ventas en este rango." : null));
        return base(c, columns, rows, rows.size(), metrics, List.of());
    }

    private Report cortesCaja(Ctx c, Filters f) {
        CashCutStatus status = enumOrNull(f.status(), CashCutStatus.class);
        Page<CashCut> page = cashCutRepository.search(c.tiendaId(), c.start(), c.end(), status, null, null, PageRequest.of(0, c.limit()));
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal salesSum = BigDecimal.ZERO;
        for (CashCut cut : page.getContent()) {
            rows.add(row(
                    "id", cut.getId(),
                    "caja", cut.getCashRegister() != null ? cut.getCashRegister().getName() : "—",
                    "cajero", cut.getUser() != null ? cut.getUser().getName() : "",
                    "apertura", iso(cut.getOpenedAt()),
                    "cierre", iso(cut.getClosedAt()),
                    "estado", CASHCUT_STATUS_LABEL.getOrDefault(cut.getStatus(), ""),
                    "fondo", cut.getOpeningAmount(),
                    "ventas", cut.getTotalSales(),
                    "efectivo", cut.getCashSales(),
                    "diferencia", cut.getCashDifference()));
            salesSum = salesSum.add(nz(cut.getTotalSales()));
        }
        List<Column> columns = List.of(
                new Column("id", "Folio", "number"),
                new Column("caja", "Caja", "text"),
                new Column("cajero", "Cajero", "text"),
                new Column("apertura", "Apertura", "datetime"),
                new Column("cierre", "Cierre", "datetime"),
                new Column("estado", "Estado", "text"),
                new Column("fondo", "Fondo inicial", "money"),
                new Column("ventas", "Ventas totales", "money"),
                new Column("efectivo", "Ventas en efectivo", "money"),
                new Column("diferencia", "Diferencia de caja", "money"));
        List<Metric> metrics = List.of(
                metric("count", "Cortes", page.getTotalElements(), "count", null),
                metric("total", "Ventas de los cortes mostrados", salesSum, "money", null));
        return base(c, columns, rows, (int) page.getTotalElements(), metrics, List.of());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────────────

    private Report base(Ctx c, List<Column> columns, List<Map<String, Object>> rows, int totalRows,
                        List<Metric> metrics, List<Chart> charts) {
        boolean truncated = rows.size() < totalRows;
        List<String> notes = truncated
                ? List.of("Se muestran " + rows.size() + " de " + totalRows + " registros. Exporta para ver todos.")
                : List.of();
        return new Report(null, null, "Fecha", c.from(), c.to(), CURRENCY, null, metrics, charts, columns, rows,
                totalRows, truncated, notes);
    }

    private static Report withNotes(Report r, List<String> extra) {
        List<String> notes = new ArrayList<>(r.notes());
        notes.addAll(extra);
        return new Report(r.type(), r.title(), r.basis(), r.from(), r.to(), r.currency(), r.generatedAt(), r.metrics(),
                r.charts(), r.columns(), r.rows(), r.totalRows(), r.truncated(), notes);
    }

    /** "Mesa 5", "Para llevar", "A domicilio (web)"… a partir de las columnas svc, mesa y origen. */
    private static String servicio(ResultSet rs) throws SQLException {
        String svc = rs.getString("svc");
        String label = "MESA".equals(svc) && rs.getString("mesa") != null
                ? "Mesa " + rs.getString("mesa")
                : SERVICIO_LABEL.getOrDefault(svc, svc);
        return "WEB".equals(rs.getString("origen")) ? label + " (web)" : label;
    }

    private static String metodos(String csv) {
        if (csv == null || csv.isBlank()) return "—";
        List<String> out = new ArrayList<>();
        for (String m : csv.split(",")) out.add(METHOD_LABEL.getOrDefault(m, m));
        return String.join(" + ", out);
    }

    /** Valor de un filtro de texto: vacío = sin filtro; fuera de la lista = 400. */
    private static String option(String value, Set<String> allowed) {
        if (value == null || value.isBlank()) return null;
        String v = value.trim().toUpperCase();
        if (!allowed.contains(v)) throw new IllegalArgumentException("Filtro no válido: " + value);
        return v;
    }

    private static Map<String, Object> row(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private static Metric metric(String key, String label, Object value, String format, String hint) {
        return new Metric(key, label, value, format, hint);
    }

    private static <E extends Enum<E>> E enumOrNull(String value, Class<E> type) {
        if (value == null || value.isBlank()) return null;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Filtro no válido: " + value);
        }
    }

    private static BigDecimal dec(Object o) {
        if (o instanceof BigDecimal b) return b;
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return BigDecimal.ZERO;
    }

    private static BigDecimal nz(BigDecimal b) {
        return b != null ? b : BigDecimal.ZERO;
    }

    private static String money(BigDecimal b) {
        return String.format("$%,.2f", b);
    }

    private static String iso(ResultSet rs, String col) throws SQLException {
        return iso(rs.getObject(col, LocalDateTime.class));
    }

    private static String iso(LocalDateTime dt) {
        return dt != null ? dt.format(DATETIME) : null;
    }

    private static String dash(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }

    private static String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) + "…" : s;
    }
}
