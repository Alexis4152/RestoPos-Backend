package com.nexora.restopos.controller;

import com.nexora.restopos.dto.ApiResponse;
import com.nexora.restopos.dto.ReservacionDtos;
import com.nexora.restopos.model.User;
import com.nexora.restopos.service.ReservacionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Pantalla "Reservaciones" del personal (sección Mesas). */
@RestController
@RequestMapping("/api/reservaciones")
@RequiredArgsConstructor
@PreAuthorize("@sectionAccess.check('TABLES')")
public class ReservacionController {

    private final ReservacionService reservacionService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ReservacionDtos.Staff>>> delDia(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(reservacionService.delDia(actor, fecha)));
    }

    @GetMapping("/pendientes")
    public ResponseEntity<ApiResponse<List<ReservacionDtos.Staff>>> pendientes(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(reservacionService.pendientes(actor)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ReservacionDtos.Staff>> crear(@Valid @RequestBody ReservacionDtos.Crear req,
                                                                   @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(reservacionService.crearPorPersonal(req, actor), "Reservación registrada"));
    }

    @PostMapping("/{id}/confirmar")
    public ResponseEntity<ApiResponse<ReservacionDtos.Staff>> confirmar(@PathVariable Long id,
                                                                       @RequestBody(required = false) ReservacionDtos.Confirmar req,
                                                                       @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(reservacionService.confirmar(id, req != null ? req.getMesaId() : null, actor),
                "Reservación confirmada"));
    }

    @PostMapping("/{id}/rechazar")
    public ResponseEntity<ApiResponse<ReservacionDtos.Staff>> rechazar(@PathVariable Long id,
                                                                      @Valid @RequestBody(required = false) ReservacionDtos.Motivo req,
                                                                      @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(reservacionService.rechazar(id, req != null ? req.getMotivo() : null, actor),
                "Reservación rechazada"));
    }

    @PostMapping("/{id}/cancelar")
    public ResponseEntity<ApiResponse<ReservacionDtos.Staff>> cancelar(@PathVariable Long id,
                                                                      @Valid @RequestBody(required = false) ReservacionDtos.Motivo req,
                                                                      @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(reservacionService.cancelar(id, req != null ? req.getMotivo() : null, actor),
                "Reservación cancelada"));
    }

    @PostMapping("/{id}/llego")
    public ResponseEntity<ApiResponse<ReservacionDtos.Staff>> llego(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(reservacionService.marcarLlegada(id, true, actor)));
    }

    @PostMapping("/{id}/no-llego")
    public ResponseEntity<ApiResponse<ReservacionDtos.Staff>> noLlego(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(reservacionService.marcarLlegada(id, false, actor)));
    }
}
