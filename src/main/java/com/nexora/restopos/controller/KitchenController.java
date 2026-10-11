package com.nexora.restopos.controller;

import com.nexora.restopos.dto.ApiResponse;
import com.nexora.restopos.dto.KitchenOrdenView;
import com.nexora.restopos.model.User;
import com.nexora.restopos.service.OrdenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador de la pantalla de Cocina, expuesto bajo {@code /api/kitchen}: vista de solo
 * lectura sobre las órdenes activas (ni pagadas ni canceladas) para que el cocinero vea qué se
 * ha pedido, agrupado por mesa/orden y en el orden en que se fue pidiendo. Requiere acceso a la
 * sección dinámica {@code KITCHEN}.
 */
@RestController
@RequestMapping("/api/kitchen")
@RequiredArgsConstructor
@PreAuthorize("@sectionAccess.check('KITCHEN')")
public class KitchenController {

    private final OrdenService ordenService;

    /**
     * Órdenes activas para cocina, más antiguas primero, opcionalmente filtradas por mesa.
     *
     * @param mesaId filtro opcional por mesa; sin él, incluye todas (para llevar incluido)
     * @param actor  usuario autenticado; determina el filtro por tienda
     */
    /** Lo que cocina ya entregó hoy (vista "Ya entregados"), lo más reciente primero. */
    @GetMapping("/delivered-today")
    public ResponseEntity<ApiResponse<List<KitchenOrdenView>>> deliveredToday(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(ordenService.findEntregadosHoy(actor), null));
    }

    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<List<KitchenOrdenView>>> orders(
            @RequestParam(required = false) Long mesaId,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(ordenService.findActiveForKitchen(mesaId, actor), null));
    }
}
