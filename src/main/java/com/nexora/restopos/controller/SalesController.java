package com.nexora.restopos.controller;

import com.nexora.restopos.dto.ApiResponse;
import com.nexora.restopos.dto.PageResponse;
import com.nexora.restopos.dto.VentaResumen;
import com.nexora.restopos.model.CuentaEstado;
import com.nexora.restopos.model.PaymentMethod;
import com.nexora.restopos.model.User;
import com.nexora.restopos.service.CuentaService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * Controlador de ventas ("Ventas"), expuesto bajo {@code /api/sales}: vista de solo lectura,
 * agrupada por {@link com.nexora.restopos.model.Orden} (una orden puede haberse cobrado con una
 * mezcla de cuentas Total/Por comensal/Personalizada — ver {@link VentaResumen}), para consultar
 * qué se ha vendido sin duplicar el modelo de datos de {@code Orden}/{@code Cuenta}/{@code Pago}
 * con una entidad "Sale" aparte. Requiere acceso a la sección dinámica {@code SALES}.
 */
@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
@PreAuthorize("@sectionAccess.check('SALES')")
public class SalesController {

    private final CuentaService cuentaService;

    /**
     * Lista paginada de ventas (órdenes, con el desglose de sus cuentas) con filtros
     * combinables por rango de fecha, estado agregado y método de pago, acotada a la tienda
     * del actor (o todas, para SUPER_ADMIN sin tienda elegida).
     *
     * @param from   fecha/hora inicial del rango (opcional)
     * @param to     fecha/hora final del rango (opcional)
     * @param estado estado agregado de la orden a filtrar (opcional, ver {@link VentaResumen})
     * @param metodo método de pago a filtrar (opcional)
     * @param page   número de página (base 0, por defecto 0)
     * @param size   tamaño de página (por defecto 20)
     * @param actor  usuario autenticado; determina el filtro por tienda
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<VentaResumen>>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) CuentaEstado estado,
            @RequestParam(required = false) PaymentMethod metodo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User actor) {
        Pageable pageable = PageRequest.of(page, size);
        Page<VentaResumen> result = cuentaService.searchVentas(from, to, estado, metodo, pageable, actor);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(result), null));
    }

    /**
     * Obtiene el detalle de una venta (orden, con el desglose de todas sus cuentas) por el id
     * de la orden, sujeto al filtro de tienda del actor.
     *
     * @param ordenId identificador de la orden
     * @param actor   usuario autenticado
     */
    @GetMapping("/{ordenId}")
    public ResponseEntity<ApiResponse<VentaResumen>> get(@PathVariable Long ordenId, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(cuentaService.findVentaByOrdenId(ordenId, actor), null));
    }
}
