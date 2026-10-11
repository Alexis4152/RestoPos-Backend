package com.nexora.restopos.controller;

import com.nexora.restopos.dto.ApiResponse;
import com.nexora.restopos.dto.CancelRequest;
import com.nexora.restopos.dto.PagoRequest;
import com.nexora.restopos.model.Pago;
import com.nexora.restopos.model.User;
import com.nexora.restopos.service.PagoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador de pagos, expuesto bajo {@code /api/accounts/{id}/payments} y
 * {@code /api/payments}. Registrar un pago requiere acceso a {@code CASH_CUTS} (quien cobra
 * debe tener su propio corte de caja abierto); cancelar un pago ya cobrado es exclusivo de ADMIN.
 */
@RestController
@RequiredArgsConstructor
public class PagoController {

    private final PagoService pagoService;

    @PostMapping("/api/accounts/{cuentaId}/payments")
    @PreAuthorize("@sectionAccess.check('CASH_CUTS')")
    public ResponseEntity<ApiResponse<Pago>> registrar(@PathVariable Long cuentaId, @Valid @RequestBody PagoRequest req,
                                                        @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(pagoService.registrar(cuentaId, req, actor), "Pago registrado"));
    }

    @PostMapping("/api/payments/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> cancelar(@PathVariable Long id, @RequestBody(required = false) CancelRequest req,
                                                       @AuthenticationPrincipal User actor) {
        pagoService.cancelar(id, req != null ? req.getMotivo() : null, actor);
        return ResponseEntity.ok(ApiResponse.ok(null, "Pago cancelado"));
    }
}
