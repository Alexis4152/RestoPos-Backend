package com.nexora.restopos.controller;

import com.nexora.restopos.dto.*;
import com.nexora.restopos.model.*;
import com.nexora.restopos.service.OrdenService;
import jakarta.validation.Valid;
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
import java.util.List;

/**
 * Controlador de órdenes (comandas), expuesto bajo {@code /api/orders}. {@code checkAny}
 * (no solo {@code ORDERS}) porque un rol con únicamente la sección {@code TAKEAWAY} también
 * necesita abrir/editar/cobrar sus órdenes para llevar a través de estos mismos endpoints —
 * no hay una entidad "orden para llevar" aparte, solo una {@code Orden} sin mesa.
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@PreAuthorize("@sectionAccess.checkAny('ORDERS', 'TAKEAWAY')")
public class OrdenController {

    private final OrdenService ordenService;
    private final com.nexora.restopos.service.OpinionService opinionService;

    /**
     * Órdenes para llevar (sin mesa) activas — ni pagadas ni canceladas —, más antiguas
     * primero, para el tablero del módulo "Para llevar".
     */
    @GetMapping("/takeaway")
    public ResponseEntity<ApiResponse<List<Orden>>> takeaway(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(ordenService.findActiveTakeaway(actor), null));
    }

    /**
     * Platos por recoger del mesero autenticado: productos de sus órdenes activas que cocina ya
     * marcó como "Entregado a mesero" y él todavía no entrega a la mesa / al cliente. El
     * frontend lo consulta cada pocos segundos para avisarle (ver PickupNotifier.jsx).
     */
    /** Costo de envío a domicilio configurado por el admin (null = no se cobra), para el alta de "Para llevar". */
    @GetMapping("/delivery-fee")
    public ResponseEntity<ApiResponse<java.math.BigDecimal>> deliveryFee(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(ordenService.costoEnvioDomicilio(actor), null));
    }

    /** Pedidos de la página web todavía sin aceptar ni rechazar (ver WebOrdersNotifier.jsx). */
    @GetMapping("/web-pending")
    public ResponseEntity<ApiResponse<List<Orden>>> webPending(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(ordenService.findPedidosWebPorAceptar(actor), null));
    }

    /** Acepta un pedido web: queda a nombre de quien lo acepta y pasa a cocina. Rechazar = POST /{id}/cancel. */
    @PostMapping("/{id}/aceptar")
    public ResponseEntity<ApiResponse<Orden>> aceptarPedidoWeb(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(ordenService.aceptarPedidoWeb(id, actor), "Pedido aceptado"));
    }

    @GetMapping("/pickups")
    public ResponseEntity<ApiResponse<List<KitchenOrdenView>>> pickups(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(ordenService.findPorRecoger(actor), null));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<Orden>>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) OrdenEstado estado,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User actor) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Orden> result = ordenService.findAll(from, to, estado, pageable, actor);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(result), null));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Orden>> get(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(ordenService.findById(id, actor), null));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Orden>> abrir(@Valid @RequestBody OrdenCreateRequest req,
                                                     @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(ordenService.abrir(req, actor), "Orden abierta"));
    }

    @PostMapping("/{id}/diners")
    public ResponseEntity<ApiResponse<Comensal>> agregarComensal(@PathVariable Long id, @RequestBody ComensalRequest req,
                                                                  @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(ordenService.agregarComensal(id, req, actor), "Comensal agregado"));
    }

    @DeleteMapping("/{id}/diners/{comensalId}")
    public ResponseEntity<ApiResponse<Void>> eliminarComensal(@PathVariable Long id, @PathVariable Long comensalId,
                                                                @AuthenticationPrincipal User actor) {
        ordenService.eliminarComensal(id, comensalId, actor);
        return ResponseEntity.ok(ApiResponse.ok(null, "Comensal eliminado"));
    }

    @PostMapping("/{id}/items")
    public ResponseEntity<ApiResponse<OrdenDetalle>> agregarItem(@PathVariable Long id, @Valid @RequestBody OrdenItemRequest req,
                                                                  @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(ordenService.agregarItem(id, req, actor), "Producto agregado"));
    }

    /** Edita una línea ya agregada (comensal, cantidad, notas, modificadores) — ver OrdenService#editarItem. */
    @PutMapping("/{id}/items/{itemId}")
    public ResponseEntity<ApiResponse<OrdenDetalle>> editarItem(@PathVariable Long id, @PathVariable Long itemId,
                                                                 @Valid @RequestBody OrdenItemUpdateRequest req,
                                                                 @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(ordenService.editarItem(id, itemId, req, actor), "Producto actualizado"));
    }

    // Override del check de clase (solo ORDERS/TAKEAWAY): también accesible desde KITCHEN,
    // para que un rol cocinero (ej. COCINERO, sin ORDERS/TAKEAWAY) pueda marcar sus propios
    // productos como preparándose/listos/entregados desde la pantalla de Cocina, igual que un
    // mesero ya puede desde el detalle de la orden — ver Kitchen.jsx/OrderDetail.jsx.
    /**
     * Actualiza el estado de preparación de una línea de la orden (ej. marcarla como
     * entregada). Accesible desde {@code ORDERS}/{@code TAKEAWAY} (mesero, desde el detalle
     * de la orden) o {@code KITCHEN} (cocinero, desde la pantalla de Cocina).
     */
    @PatchMapping("/{id}/items/{itemId}/estado")
    @PreAuthorize("@sectionAccess.checkAny('ORDERS', 'TAKEAWAY', 'KITCHEN')")
    public ResponseEntity<ApiResponse<OrdenDetalle>> actualizarEstadoItem(@PathVariable Long id, @PathVariable Long itemId,
                                                                           @RequestParam OrdenDetalleEstado estado,
                                                                           @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(ordenService.actualizarEstadoItem(id, itemId, estado, actor), null));
    }

    @DeleteMapping("/{id}/items/{itemId}")
    public ResponseEntity<ApiResponse<Void>> cancelarItem(@PathVariable Long id, @PathVariable Long itemId,
                                                           @RequestBody(required = false) CancelRequest req,
                                                           @AuthenticationPrincipal User actor) {
        ordenService.cancelarItem(id, itemId, req != null ? req.getMotivo() : null, actor);
        return ResponseEntity.ok(ApiResponse.ok(null, "Producto cancelado"));
    }

    /** Liga la orden a un cliente registrado (por correo o teléfono) para que la vea y la califique en "Mis pedidos". */
    @PostMapping("/{id}/cliente")
    public ResponseEntity<ApiResponse<Orden>> vincularCliente(@PathVariable Long id, @Valid @RequestBody OpinionDtos.VincularCliente req,
                                                              @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(ordenService.vincularCliente(id, req.getBusqueda(), actor), "Cliente ligado a la orden"));
    }

    @DeleteMapping("/{id}/cliente")
    public ResponseEntity<ApiResponse<Orden>> desvincularCliente(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(ordenService.desvincularCliente(id, actor), "Cliente quitado de la orden"));
    }

    /** Opiniones de las cuentas de la orden (cuentaId → opinión). */
    @GetMapping("/{id}/opiniones")
    public ResponseEntity<ApiResponse<java.util.Map<Long, OpinionDtos.OpinionHecha>>> opiniones(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        ordenService.findById(id, actor); // valida acceso a la orden
        return ResponseEntity.ok(ApiResponse.ok(opinionService.deOrden(id)));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelar(@PathVariable Long id, @RequestBody(required = false) CancelRequest req,
                                                       @AuthenticationPrincipal User actor) {
        ordenService.cancelar(id, req != null ? req.getMotivo() : null, actor);
        return ResponseEntity.ok(ApiResponse.ok(null, "Orden cancelada"));
    }
}
