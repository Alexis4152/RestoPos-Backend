package com.nexora.restopos.controller;

import com.nexora.restopos.dto.ApiResponse;
import com.nexora.restopos.dto.CancelRequest;
import com.nexora.restopos.dto.CuentaCreateRequest;
import com.nexora.restopos.dto.TicketEmailRequest;
import com.nexora.restopos.model.Cuenta;
import com.nexora.restopos.model.User;
import com.nexora.restopos.service.CuentaService;
import com.nexora.restopos.service.EmailService;
import com.nexora.restopos.service.EscPosTicketService;
import com.nexora.restopos.service.TicketPdfService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador de cuentas (cobro de una orden, total/por comensal/personalizada) y su ticket,
 * expuesto bajo {@code /api/orders/{ordenId}/accounts} y {@code /api/accounts}.
 */
@RestController
@RequiredArgsConstructor
public class CuentaController {

    private final CuentaService cuentaService;
    private final TicketPdfService ticketPdfService;
    private final EscPosTicketService escPosTicketService;
    private final EmailService emailService;

    @PostMapping("/api/orders/{ordenId}/accounts")
    @PreAuthorize("@sectionAccess.check('ORDERS')")
    public ResponseEntity<ApiResponse<Cuenta>> crear(@PathVariable Long ordenId, @Valid @RequestBody CuentaCreateRequest req,
                                                      @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(cuentaService.crearEnCaja(ordenId, req, actor), "Cuenta generada"));
    }

    @GetMapping("/api/orders/{ordenId}/accounts")
    @PreAuthorize("@sectionAccess.check('ORDERS')")
    public ResponseEntity<ApiResponse<List<Cuenta>>> listByOrden(@PathVariable Long ordenId, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(cuentaService.findAllByOrden(ordenId, actor), null));
    }

    @GetMapping("/api/accounts/{id}")
    @PreAuthorize("@sectionAccess.check('ORDERS')")
    public ResponseEntity<ApiResponse<Cuenta>> get(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(cuentaService.findById(id, actor), null));
    }

    @PostMapping("/api/accounts/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'MESERO')")
    public ResponseEntity<ApiResponse<Void>> cancelar(@PathVariable Long id, @RequestBody(required = false) CancelRequest req,
                                                       @AuthenticationPrincipal User actor) {
        cuentaService.cancelar(id, req != null ? req.getMotivo() : null, actor);
        return ResponseEntity.ok(ApiResponse.ok(null, "Cuenta cancelada"));
    }

    @GetMapping("/api/accounts/{id}/ticket")
    @PreAuthorize("@sectionAccess.check('ORDERS')")
    public ResponseEntity<byte[]> ticket(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        Cuenta cuenta = cuentaService.findById(id, actor);
        byte[] pdf = ticketPdfService.generate(cuenta);
        String folio = cuenta.getTicketFolio() != null ? cuenta.getTicketFolio() : String.valueOf(cuenta.getId());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"ticket-" + folio + ".pdf\"")
                .body(pdf);
    }

    /**
     * Arma el ticket de una cuenta ya cobrada en formato ESC/POS, listo para que el navegador
     * se lo pase directo a QZ Tray (ver {@link EscPosTicketService} para el porqué del
     * formato). Requiere acceso a {@code ORDERS} o {@code SALES}: se usa tanto al cobrar
     * (Detalle de orden) como al reimprimir desde el módulo de Ventas.
     */
    @GetMapping("/api/accounts/{id}/ticket-escpos")
    @PreAuthorize("@sectionAccess.checkAny('ORDERS', 'SALES')")
    public ResponseEntity<ApiResponse<String>> ticketEscPos(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        Cuenta cuenta = cuentaService.findById(id, actor);
        return ResponseEntity.ok(ApiResponse.ok(escPosTicketService.build(cuenta)));
    }

    @PostMapping("/api/accounts/{id}/ticket/email")
    @PreAuthorize("@sectionAccess.check('ORDERS')")
    public ResponseEntity<ApiResponse<Void>> emailTicket(@PathVariable Long id, @Valid @RequestBody TicketEmailRequest req,
                                                          @AuthenticationPrincipal User actor) {
        Cuenta cuenta = cuentaService.findById(id, actor);
        emailService.sendTicketEmail(cuenta, req.getEmail());
        return ResponseEntity.ok(ApiResponse.ok(null, "Ticket enviado"));
    }
}
