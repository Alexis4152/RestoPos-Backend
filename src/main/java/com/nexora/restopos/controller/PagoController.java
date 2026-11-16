package com.nexora.restopos.controller;

import com.nexora.restopos.dto.ApiResponse;
import com.nexora.restopos.dto.CancelRequest;
import com.nexora.restopos.dto.PagoCorreccionRequest;
import com.nexora.restopos.dto.PagoDevolucionRequest;
import com.nexora.restopos.dto.PagoRequest;
import com.nexora.restopos.dto.TransferenciaPorVerificar;
import com.nexora.restopos.model.Pago;
import com.nexora.restopos.model.User;
import com.nexora.restopos.service.PagoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
    public ResponseEntity<ApiResponse<Void>> cancelar(@PathVariable Long id, @RequestBody(required = false) CancelRequest req,
                                                       @AuthenticationPrincipal User actor) {
        pagoService.cancelar(id, req != null ? req.getMotivo() : null, actor);
        return ResponseEntity.ok(ApiResponse.ok(null, "Pago cancelado"));
    }

    /** Corrige el método de un pago mal capturado (solo con su corte abierto). */
    @PostMapping("/api/payments/{id}/metodo")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
    public ResponseEntity<ApiResponse<Pago>> corregirMetodo(@PathVariable Long id, @Valid @RequestBody PagoCorreccionRequest req,
                                                             @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(pagoService.corregirMetodo(id, req, actor), "Método de pago corregido"));
    }

    /** Devuelve dinero de un pago que ya no se puede cancelar (sale del corte abierto de quien devuelve). */
    @PostMapping("/api/payments/{id}/devolver")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
    public ResponseEntity<ApiResponse<Pago>> devolver(@PathVariable Long id, @Valid @RequestBody PagoDevolucionRequest req,
                                                       @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(pagoService.devolver(id, req, actor), "Devolución registrada"));
    }

    /** Transferencias por confirmar contra el banco. */
    @GetMapping("/api/payments/por-verificar")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
    public ResponseEntity<ApiResponse<List<TransferenciaPorVerificar>>> porVerificar(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(pagoService.porVerificar(actor)));
    }

    /** Confirma una transferencia ({@code resultado}: VERIFICADA o NO_RECIBIDA). */
    @PostMapping("/api/payments/{id}/verificar")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
    public ResponseEntity<ApiResponse<Pago>> verificar(@PathVariable Long id, @RequestParam String resultado,
                                                        @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(pagoService.verificar(id, resultado, actor), "Transferencia actualizada"));
    }
}
