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
    public ResponseEntity<ApiResponse<Orden>> soltar(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(deliveryService.soltar(id, actor), "Pedido liberado"));
    }

    @PostMapping("/{id}/entregar")
    public ResponseEntity<ApiResponse<Orden>> entregar(@PathVariable Long id, @Valid @RequestBody EntregaRequest req,
                                                       @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(deliveryService.entregar(id, req, actor), "Pedido entregado"));
    }
}
