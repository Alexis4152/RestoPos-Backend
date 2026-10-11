package com.nexora.restopos.controller;

import com.nexora.restopos.dto.ApiResponse;
import com.nexora.restopos.dto.CashCutScheduleRequest;
import com.nexora.restopos.model.CashCutSchedule;
import com.nexora.restopos.model.User;
import com.nexora.restopos.service.CashCutScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

// Horario global (una sola fila) del cierre automático de cortes de caja — lo administra
// solo el SUPER_ADMIN, ya que aplica a todas las tiendas por igual.
/**
 * Controlador del horario global de cierre automático de cortes de caja,
 * expuesto bajo {@code /api/cash-cuts/schedule}.
 * <p>
 * Representa una única configuración (no hay una por tienda), por lo que solo
 * el SUPER_ADMIN puede consultarla o modificarla; el {@code @PreAuthorize} de
 * clase aplica a todos los métodos del controlador.
 */
@RestController
@RequestMapping("/api/cash-cuts/schedule")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class CashCutScheduleController {

    private final CashCutScheduleService scheduleService;

    /**
     * Obtiene la configuración vigente del horario de cierre automático de
     * cortes de caja.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<CashCutSchedule>> get() {
        return ResponseEntity.ok(ApiResponse.ok(scheduleService.get(), null));
    }

    /**
     * Actualiza la configuración del horario de cierre automático de cortes de
     * caja, aplicable a todas las tiendas.
     *
     * @param req   nuevos datos del horario
     * @param actor usuario autenticado que realiza el cambio (SUPER_ADMIN)
     */
    @PutMapping
    public ResponseEntity<ApiResponse<CashCutSchedule>> update(@Valid @RequestBody CashCutScheduleRequest req, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(scheduleService.update(req, actor), "Horario actualizado"));
    }
}
