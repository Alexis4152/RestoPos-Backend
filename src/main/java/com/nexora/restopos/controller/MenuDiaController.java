package com.nexora.restopos.controller;

import com.nexora.restopos.dto.ApiResponse;
import com.nexora.restopos.dto.MenuDiaDtos;
import com.nexora.restopos.model.User;
import com.nexora.restopos.service.MenuDiaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Menús con nombre y "el menú de hoy" (sección Menú). Leerlos también lo necesitan quienes
 * toman órdenes, para saber qué se vende hoy. Crear/editar/elegir el de hoy: sección MENU.
 */
@RestController
@RequestMapping("/api/menus")
@RequiredArgsConstructor
public class MenuDiaController {

    private final MenuDiaService menuDiaService;

    @GetMapping
    @PreAuthorize("@sectionAccess.checkAny('MENU', 'ORDERS', 'TAKEAWAY')")
    public ResponseEntity<ApiResponse<List<MenuDiaDtos.Menu>>> list(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(menuDiaService.list(actor)));
    }

    @PostMapping
    @PreAuthorize("@sectionAccess.check('MENU')")
    public ResponseEntity<ApiResponse<MenuDiaDtos.Menu>> create(@Valid @RequestBody MenuDiaDtos.Guardar req,
                                                                @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(menuDiaService.create(req, actor), "Menú guardado"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@sectionAccess.check('MENU')")
    public ResponseEntity<ApiResponse<MenuDiaDtos.Menu>> update(@PathVariable Long id, @Valid @RequestBody MenuDiaDtos.Guardar req,
                                                                @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(menuDiaService.update(id, req, actor), "Menú guardado"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@sectionAccess.check('MENU')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        menuDiaService.delete(id, actor);
        return ResponseEntity.ok(ApiResponse.ok(null, "Menú eliminado"));
    }

    /** Lo vuelve el menú de hoy. */
    @PostMapping("/{id}/hoy")
    @PreAuthorize("@sectionAccess.check('MENU')")
    public ResponseEntity<ApiResponse<MenuDiaDtos.Menu>> usarHoy(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(menuDiaService.usarHoy(id, actor), "Ahora es el menú de hoy"));
    }

    /** Quita el menú de hoy: se vuelve a vender todo el catálogo. */
    @DeleteMapping("/hoy")
    @PreAuthorize("@sectionAccess.check('MENU')")
    public ResponseEntity<ApiResponse<Void>> quitarHoy(@AuthenticationPrincipal User actor) {
        menuDiaService.quitarHoy(actor);
        return ResponseEntity.ok(ApiResponse.ok(null, "Sin menú de hoy: se vende todo el catálogo"));
    }
}
