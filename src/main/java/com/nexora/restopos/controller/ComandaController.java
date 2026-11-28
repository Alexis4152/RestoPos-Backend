package com.nexora.restopos.controller;

import com.nexora.restopos.dto.ApiResponse;
import com.nexora.restopos.model.Comanda;
import com.nexora.restopos.model.User;
import com.nexora.restopos.service.ComandaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Cola de comandas por imprimir (ver {@link Comanda}), bajo {@code /api/comandas}:
 * <ul>
 *   <li>{@code POST /api/orders/{id}/comandas}: el mesero registra lo que acaba de mandar.</li>
 *   <li>{@code GET /api/comandas/pendientes}, {@code POST /{id}/tomar}, {@code POST /{id}/liberar}:
 *       la estación de impresión consulta, toma (imprime) y, si falla, libera.</li>
 * </ul>
 */
@RestController
@RequiredArgsConstructor
public class ComandaController {

    private final ComandaService comandaService;

    @PostMapping("/api/orders/{ordenId}/comandas")
    @PreAuthorize("@sectionAccess.checkAny('ORDERS', 'TAKEAWAY')")
    public ResponseEntity<ApiResponse<Comanda>> crear(@PathVariable Long ordenId,
                                                      @RequestBody Map<String, List<Long>> body,
                                                      @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(comandaService.crear(ordenId, body.get("detalleIds"), actor)));
    }

    /** Envíos a cocina de la orden (para elegir cuál reimprimir). */
    @GetMapping("/api/orders/{ordenId}/comandas")
    @PreAuthorize("@sectionAccess.checkAny('ORDERS', 'TAKEAWAY')")
    public ResponseEntity<ApiResponse<List<ComandaService.EnvioComanda>>> envios(@PathVariable Long ordenId,
                                                                               @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(comandaService.envios(ordenId, actor)));
    }

    /** Reimprime en cocina un envío ({"comandaId": id}) o toda la orden (sin comandaId). */
    @PostMapping("/api/orders/{ordenId}/comandas/reimprimir")
    @PreAuthorize("@sectionAccess.checkAny('ORDERS', 'TAKEAWAY')")
    public ResponseEntity<ApiResponse<Comanda>> reimprimir(@PathVariable Long ordenId,
                                                           @RequestBody(required = false) Map<String, Long> body,
                                                           @AuthenticationPrincipal User actor) {
        Long comandaId = body != null ? body.get("comandaId") : null;
        return ResponseEntity.ok(ApiResponse.ok(comandaService.reimprimir(ordenId, comandaId, actor), "Se mandó a imprimir"));
    }

    @GetMapping("/api/comandas/pendientes")
    @PreAuthorize("@sectionAccess.checkAny('ORDERS', 'TAKEAWAY', 'CASH_CUTS', 'KITCHEN')")
    public ResponseEntity<ApiResponse<List<Comanda>>> pendientes(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(comandaService.pendientes(actor)));
    }

    /** Devuelve {@code {destino, hex}} para imprimir, o {@code null} si otra estación ya la tomó. */
    @PostMapping("/api/comandas/{id}/tomar")
    @PreAuthorize("@sectionAccess.checkAny('ORDERS', 'TAKEAWAY', 'CASH_CUTS', 'KITCHEN')")
    public ResponseEntity<ApiResponse<Map<String, String>>> tomar(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(comandaService.tomar(id, actor)));
    }

    @PostMapping("/api/comandas/{id}/liberar")
    @PreAuthorize("@sectionAccess.checkAny('ORDERS', 'TAKEAWAY', 'CASH_CUTS', 'KITCHEN')")
    public ResponseEntity<ApiResponse<Void>> liberar(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        comandaService.liberar(id, actor);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
