package com.nexora.restopos.controller;

import com.nexora.restopos.dto.ApiResponse;
import com.nexora.restopos.model.User;
import com.nexora.restopos.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Controlador de reportes, expuesto bajo {@code /api/reports}.
 * <p>
 * Expone consultas agregadas de ventas e inventario (resúmenes, productos más
 * vendidos, ventas por día, estado de inventario y movimientos de un producto)
 * para la tienda del usuario autenticado. La mayoría de los endpoints requiere
 * acceso a la sección {@code REPORTS}; el resumen de ventas también admite el
 * acceso desde {@code DASHBOARD} porque alimenta un widget del panel principal.
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    // usado también por el widget del Dashboard, por eso admite ambas secciones
    /**
     * Resumen agregado de ventas (totales, tickets, etc.) dentro del rango de
     * fechas indicado. Accesible desde {@code REPORTS} o {@code DASHBOARD}
     * (este último usado por un widget del panel principal).
     *
     * @param from  fecha inicial del rango (inclusive)
     * @param to    fecha final del rango (inclusive)
     * @param actor usuario autenticado; determina el filtro por tienda
     */
    @GetMapping("/sales-summary")
    @PreAuthorize("@sectionAccess.checkAny('REPORTS', 'DASHBOARD')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> salesSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.salesSummary(from, to, actor), null));
    }

    /**
     * Lista los productos más vendidos dentro del rango de fechas indicado,
     * limitado a los {@code limit} primeros. Requiere acceso a {@code REPORTS}.
     *
     * @param from  fecha inicial del rango (inclusive)
     * @param to    fecha final del rango (inclusive)
     * @param limit cantidad máxima de productos a devolver (por defecto 10)
     * @param actor usuario autenticado; determina el filtro por tienda
     */
    @GetMapping("/top-products")
    @PreAuthorize("@sectionAccess.check('REPORTS')")
    public ResponseEntity<ApiResponse<List<Object[]>>> topProducts(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") int limit,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.topProducts(from, to, limit, actor), null));
    }

    /**
     * Devuelve el total de ventas agrupado por día dentro del rango indicado.
     * Requiere acceso a {@code REPORTS}.
     *
     * @param from  fecha inicial del rango (inclusive)
     * @param to    fecha final del rango (inclusive)
     * @param actor usuario autenticado; determina el filtro por tienda
     */
    @GetMapping("/sales-by-day")
    @PreAuthorize("@sectionAccess.check('REPORTS')")
    public ResponseEntity<ApiResponse<List<Object[]>>> salesByDay(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.salesByDay(from, to, actor), null));
    }

    /**
     * Devuelve el estado actual del inventario de la tienda (totales, stock
     * bajo, etc.). Requiere acceso a {@code REPORTS}.
     *
     * @param actor usuario autenticado; determina el filtro por tienda
     */
    @GetMapping("/inventory-status")
    @PreAuthorize("@sectionAccess.check('REPORTS')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> inventoryStatus(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.inventoryStatus(actor), null));
    }

    /**
     * Lista los movimientos de inventario (entradas, salidas, ajustes) de un
     * producto específico dentro del rango de fechas indicado. Requiere acceso
     * a {@code REPORTS}.
     *
     * @param productId identificador del producto
     * @param from      fecha inicial del rango (inclusive)
     * @param to        fecha final del rango (inclusive)
     * @param actor     usuario autenticado; determina el filtro por tienda
     */
    @GetMapping("/inventory-movements/{productId}")
    @PreAuthorize("@sectionAccess.check('REPORTS')")
    public ResponseEntity<ApiResponse<List<Object[]>>> movements(
            @PathVariable Long productId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.movementsByProduct(productId, from, to, actor), null));
    }
}
