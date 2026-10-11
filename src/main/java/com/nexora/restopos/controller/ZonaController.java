package com.nexora.restopos.controller;

import com.nexora.restopos.dto.ApiResponse;
import com.nexora.restopos.dto.ZonaRequest;
import com.nexora.restopos.model.Zona;
import com.nexora.restopos.model.User;
import com.nexora.restopos.service.ZonaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador de zonas, expuesto bajo {@code /api/zonas}. Consulta disponible desde las mismas
 * secciones que Mesas (se elige la zona antes de elegir la mesa en el tablero); creación,
 * edición y eliminación reservadas a ADMIN — mismo patrón que {@code CategoryController}.
 */
@RestController
@RequestMapping("/api/zonas")
@RequiredArgsConstructor
public class ZonaController {

    private final ZonaService zonaService;

    @GetMapping
    @PreAuthorize("@sectionAccess.checkAny('TABLES', 'ORDERS', 'DASHBOARD')")
    public ResponseEntity<ApiResponse<List<Zona>>> list(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(zonaService.findAll(actor), null));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@sectionAccess.checkAny('TABLES', 'ORDERS')")
    public ResponseEntity<ApiResponse<Zona>> get(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(zonaService.findById(id, actor), null));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Zona>> create(@Valid @RequestBody ZonaRequest req, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(zonaService.create(req, actor), "Zona creada"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Zona>> update(@PathVariable Long id, @Valid @RequestBody ZonaRequest req,
                                                     @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(zonaService.update(id, req, actor), "Zona actualizada"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        zonaService.delete(id, actor);
        return ResponseEntity.ok(ApiResponse.ok(null, "Zona eliminada"));
    }
}
