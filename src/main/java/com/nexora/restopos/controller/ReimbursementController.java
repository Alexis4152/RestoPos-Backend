package com.nexora.restopos.controller;

import com.nexora.restopos.dto.ApiResponse;
import com.nexora.restopos.dto.PageResponse;
import com.nexora.restopos.dto.ReimbursementPayRequest;
import com.nexora.restopos.model.Reimbursement;
import com.nexora.restopos.model.ReimbursementStatus;
import com.nexora.restopos.model.User;
import com.nexora.restopos.service.ReimbursementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * Reembolsos ({@code /api/reimbursements}): dinero que la tienda debe a quien puso efectivo
 * de su bolsa. Consultarlos lo necesita cualquiera con {@code CASH_CUTS} (el cajero los ve
 * en la salida "Reembolso" de su corte); pagarlos desde aquí, solo administradores.
 */
@RestController
@RequestMapping("/api/reimbursements")
@RequiredArgsConstructor
public class ReimbursementController {

    private final ReimbursementService reimbursementService;

    @GetMapping
    @PreAuthorize("@sectionAccess.check('CASH_CUTS')")
    public ResponseEntity<ApiResponse<PageResponse<Reimbursement>>> list(@RequestParam(required = false) ReimbursementStatus status,
                                                                         @RequestParam(defaultValue = "0") int page,
                                                                         @RequestParam(defaultValue = "20") int size,
                                                                         @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(reimbursementService.search(status, PageRequest.of(page, size), actor)), null));
    }

    @GetMapping("/pending-total")
    @PreAuthorize("@sectionAccess.check('CASH_CUTS')")
    public ResponseEntity<ApiResponse<BigDecimal>> pendingTotal(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(reimbursementService.pendingTotal(actor), null));
    }

    @PostMapping("/{id}/pay")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
    public ResponseEntity<ApiResponse<Reimbursement>> pay(@PathVariable Long id,
                                                          @Valid @RequestBody ReimbursementPayRequest req,
                                                          @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(reimbursementService.pay(id, req, actor), "Reembolso pagado"));
    }
}
