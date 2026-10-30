package com.nexora.restopos.dto.catalog;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Forma común de todos los reportes del catálogo (ver {@code ReportCatalogService}):
 * indicadores, gráficas y una tabla. La misma estructura alimenta la pantalla de
 * "Reportes" y sus exportaciones (CSV, Excel y PDF, ver {@code ReportCatalogExporter}),
 * así lo que se exporta es exactamente lo que se ve en pantalla.
 *
 * <p>Mismo patrón que el módulo de reportes de DemoPV (catálogo declarativo + un endpoint
 * genérico {@code /api/reports/{type}}), con reportes propios del restaurante: cuentas,
 * platillos, servicio, meseros, reservaciones, inventario y cortes de caja.</p>
 */
public final class ReportCatalogDtos {

    private ReportCatalogDtos() {
    }

    /** Formato de una celda/columna: text, money, count, number, percent, date, datetime. */
    public record Column(String key, String label, String format) {
    }

    /** {@code hint} explica qué representa la cifra, cuando no es obvio por el nombre. */
    public record Metric(String key, String label, Object value, String format, String hint) {
    }

    public record Point(String label, List<Number> values) {
    }

    /** {@code kind}: bar | line. {@code series} nombra cada valor de los puntos. */
    public record Chart(String key, String title, String kind, String format, List<String> series, List<Point> points) {
    }

    public record Report(String type, String title, String basis, LocalDate from, LocalDate to, String currency,
                          Instant generatedAt, List<Metric> metrics, List<Chart> charts, List<Column> columns,
                          List<Map<String, Object>> rows, int totalRows, boolean truncated, List<String> notes) {
    }

    /**
     * Filtros aceptados; cada reporte usa los que declara en su {@link ReportInfo#filters()}
     * y ninguno de los demás.
     *
     * @param status según el reporte: estado de reservación (PENDIENTE/CONFIRMADA/...) o de
     *               corte de caja (OPEN/CLOSED)
     * @param method método de pago (CASH/CARD/TRANSFER)
     * @param movementType tipo de movimiento de inventario (IN/OUT/ADJUSTMENT/SALE)
     * @param servicio tipo de servicio: MESA, PARA_LLEVAR, COMER_AQUI, RECOGER, DOMICILIO
     */
    public record Filters(LocalDate from, LocalDate to, String status, String method, String movementType,
                          String servicio) {
    }

    /** Catálogo de reportes visible para el usuario actual. {@code snapshot} = no depende
     *  de un periodo (situación actual, ej. stock bajo ahora mismo). */
    public record ReportInfo(String type, String title, String description, String group, boolean snapshot,
                              List<String> filters) {
    }

    public record ExportFile(String filename, String contentType, byte[] content) {
    }
}
