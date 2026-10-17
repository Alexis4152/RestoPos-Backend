package com.nexora.restopos.controller;

import com.nexora.restopos.dto.ApiResponse;
import com.nexora.restopos.dto.PageResponse;
import com.nexora.restopos.dto.ShortagePaymentRequest;
import com.nexora.restopos.model.Shortage;
import com.nexora.restopos.model.ShortagePayment;
import com.nexora.restopos.model.User;
import com.nexora.restopos.service.ShortageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Faltantes ({@code /api/shortages}): dinero que una persona debe cubrir porque faltó en
 * una entrega de caja, y sus pagos/abonos. Solo administradores/supervisores.
 */
@RestController
@RequestMapping("/api/shortages")
@RequiredArgsConstructor
public class ShortageController {

    private final ShortageService shortageService;

    /** {@code paid=false} (default): pendientes y con abonos; {@code paid=true}: pagados. */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
    public ResponseEntity<ApiResponse<PageResponse<Shortage>>> list(@RequestParam(defaultValue = "false") boolean paid,
                                                                    @RequestParam(defaultValue = "0") int page,
                                                                    @RequestParam(defaultValue = "20") int size,
                                                                    @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(shortageService.search(paid, PageRequest.of(page, size), actor)), null));
    }

    @GetMapping("/pending-total")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
    public ResponseEntity<ApiResponse<BigDecimal>> pendingTotal(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(shortageService.pendingTotal(actor), null));
    }

    @GetMapping("/{id}/payments")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
    public ResponseEntity<ApiResponse<List<ShortagePayment>>> payments(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(shortageService.payments(id, actor), null));
    }

    @PostMapping("/{id}/payments")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
    public ResponseEntity<ApiResponse<Shortage>> pay(@PathVariable Long id,
                                                     @Valid @RequestBody ShortagePaymentRequest req,
                                                     @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(shortageService.pay(id, req, actor), "Pago registrado"));
    }
}
