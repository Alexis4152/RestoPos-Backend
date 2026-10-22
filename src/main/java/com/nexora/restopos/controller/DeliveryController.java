package com.nexora.restopos.controller;

import com.nexora.restopos.dto.ApiResponse;
import com.nexora.restopos.dto.EntregaRequest;
import com.nexora.restopos.model.Orden;
import com.nexora.restopos.model.User;
import com.nexora.restopos.service.DeliveryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Pantalla "Entregas" del repartidor (sección DELIVERY) — ver {@link DeliveryService}. */
@RestController
@RequestMapping("/api/deliveries")
@RequiredArgsConstructor
@PreAuthorize("@sectionAccess.check('DELIVERY')")
public class DeliveryController {

    private final DeliveryService deliveryService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Orden>>> pendientes(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(deliveryService.pendientes(actor)));
    }

    /** Banco, titular y CLABE de la tienda, para mostrárselos al cliente que paga por transferencia. */
    @GetMapping("/transfer-info")
    public ResponseEntity<ApiResponse<Map<String, String>>> transferInfo(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(deliveryService.datosTransferencia(actor)));
    }

    @PostMapping("/{id}/tomar")
    public ResponseEntity<ApiResponse<Orden>> tomar(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(deliveryService.tomar(id, actor), "Pedido tomado"));
    }

    /** "Voy en camino": habilitado cuando el restaurante ya le entregó el pedido. */
    @PostMapping("/{id}/en-camino")
    public ResponseEntity<ApiResponse<Orden>> enCamino(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(deliveryService.enCamino(id, actor), "Vas en camino"));
    }

    @PostMapping("/{id}/soltar")
    public ResponseEntity<ApiResponse<Orden>> soltar(@PathVariable Long id,
                                                     @RequestBody(required = false) com.nexora.restopos.dto.CancelRequest req,
                                                     @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(deliveryService.soltar(id, req != null ? req.getMotivo() : null, actor), "Pedido liberado"));
    }

    /** Historial del repartidor (sus entregas); por default, los últimos 7 días. */
    @GetMapping("/historial")
    public ResponseEntity<ApiResponse<List<Orden>>> historial(
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate desde,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate hasta,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(deliveryService.historial(actor, desde, hasta)));
    }

    /** Caja: envíos entregados cuyo dinero todavía trae el repartidor. */
    @GetMapping("/por-recibir")
    @PreAuthorize("@sectionAccess.checkAny('TAKEAWAY', 'ORDERS', 'CASH_CUTS')")
    public ResponseEntity<ApiResponse<List<Orden>>> porRecibir(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(deliveryService.porRecibir(actor)));
    }

    /** Caja: recibe del repartidor el dinero de un envío (se cobra en el corte de quien recibe). */
    @PostMapping("/{id}/recibir")
    @PreAuthorize("@sectionAccess.check('CASH_CUTS')") // solo quien cobra (cajero/admin)
    public ResponseEntity<ApiResponse<Orden>> recibir(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(deliveryService.recibirDinero(id, actor), "Dinero recibido"));
    }

    @PostMapping("/{id}/entregar")
    public ResponseEntity<ApiResponse<Orden>> entregar(@PathVariable Long id, @Valid @RequestBody EntregaRequest req,
                                                       @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(deliveryService.entregar(id, req, actor), "Pedido entregado"));
    }
}
