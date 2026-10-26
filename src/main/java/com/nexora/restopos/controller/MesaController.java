package com.nexora.restopos.controller;

import com.nexora.restopos.dto.ApiResponse;
import com.nexora.restopos.dto.MesaRequest;
import com.nexora.restopos.model.Mesa;
import com.nexora.restopos.model.User;
import com.nexora.restopos.service.MesaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Controlador de mesas, expuesto bajo {@code /api/tables}. */
@RestController
@RequestMapping("/api/tables")
@RequiredArgsConstructor
public class MesaController {

    private final MesaService mesaService;

    @GetMapping
    @PreAuthorize("@sectionAccess.checkAny('TABLES', 'ORDERS', 'DASHBOARD')")
    public ResponseEntity<ApiResponse<List<Mesa>>> list(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(mesaService.findAll(actor), null));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@sectionAccess.checkAny('TABLES', 'ORDERS')")
    public ResponseEntity<ApiResponse<Mesa>> get(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(mesaService.findById(id, actor), null));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Mesa>> create(@Valid @RequestBody MesaRequest req, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(mesaService.create(req, actor), "Mesa creada"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Mesa>> update(@PathVariable Long id, @Valid @RequestBody MesaRequest req,
                                                     @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(mesaService.update(id, req, actor), "Mesa actualizada"));
    }

    /** Qué mesas se pueden reservar. Body: {"mesaIds": [1, 4, 7]} (las demás quedan sin reservar). */
    @PutMapping("/reservables")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> reservables(@RequestBody java.util.Map<String, List<Long>> body,
                                                         @AuthenticationPrincipal User actor) {
        mesaService.setReservables(body == null ? List.of() : body.getOrDefault("mesaIds", List.of()), actor);
        return ResponseEntity.ok(ApiResponse.ok(null, "Mesas para reservar actualizadas"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        mesaService.deactivate(id, actor);
        return ResponseEntity.ok(ApiResponse.ok(null, "Mesa desactivada"));
    }
}
