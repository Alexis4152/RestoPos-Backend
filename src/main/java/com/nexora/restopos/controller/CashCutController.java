package com.nexora.restopos.controller;

import com.nexora.restopos.dto.ApiResponse;
import com.nexora.restopos.dto.ArqueoResolveRequest;
import com.nexora.restopos.dto.CashCutCloseRequest;
import com.nexora.restopos.dto.CashCutForceCloseRequest;
import com.nexora.restopos.dto.CashCutOpenRequest;
import com.nexora.restopos.dto.CashMovementRequest;
import com.nexora.restopos.dto.CashCutSummary;
import com.nexora.restopos.dto.PageResponse;
import com.nexora.restopos.model.CashCut;
import com.nexora.restopos.model.CashCutStatus;
import com.nexora.restopos.model.CashMovement;
import com.nexora.restopos.model.HandoverStatus;
import com.nexora.restopos.model.User;
import com.nexora.restopos.service.CashCutService;
import com.nexora.restopos.service.CashMovementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Controlador de cortes de caja ("CashCut"), expuesto bajo {@code /api/cash-cuts}.
 * <p>
 * Cada cajero/vendedor abre su corte recibiendo una caja y lo cierra con arqueo
 * (efectivo esperado vs. contado); el historial completo en forma de tabla solo lo puede consultar
 * un ADMIN (de su propia tienda) o SUPER_ADMIN (de todas). El resto de las
 * operaciones sobre el corte propio están protegidas por la sección dinámica
 * {@code CASH_CUTS} (o {@code DASHBOARD} cuando el dato se reutiliza en un widget).
 */
@RestController
@RequestMapping("/api/cash-cuts")
@RequiredArgsConstructor
public class CashCutController {

    private final CashCutService cashCutService;
    private final CashMovementService cashMovementService;

    // tabla/historial de cortes: solo ADMIN (de su propia tienda) o SUPER_ADMIN (todas)
    /**
     * Lista paginada del historial de cortes de caja, con filtros opcionales por
     * rango de fechas y estado. Solo accesible para ADMIN (limitado a su tienda)
     * o SUPER_ADMIN (todas las tiendas).
     *
     * @param from   fecha/hora inicial del rango (opcional)
     * @param to     fecha/hora final del rango (opcional)
     * @param status estado del corte a filtrar (opcional)
     * @param userId cajero dueño del corte a filtrar (opcional, ver {@link #cashiers})
     * @param page   número de página (base 0, por defecto 0)
     * @param size   tamaño de página (por defecto 20)
     * @param actor  usuario autenticado; determina el filtro por tienda
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
    public ResponseEntity<ApiResponse<PageResponse<CashCut>>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) CashCutStatus status,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) HandoverStatus arqueoStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User actor) {
        Pageable pageable = PageRequest.of(page, size);
        Page<CashCut> result = cashCutService.findAll(from, to, status, userId, arqueoStatus, pageable, actor);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(result), null));
    }

    /**
     * Cajeros distintos con al menos un corte de caja en la tienda del actor, para poblar
     * el filtro "Cajero" del historial — a propósito NO reusa {@code UserController} (que
     * exige la sección {@code USERS}, que un ADMIN podría no tener habilitada): esta lista
     * solo trae id/nombre y cuelga del mismo permiso que el propio historial.
     *
     * @param actor usuario autenticado; determina el filtro por tienda
     */
    @GetMapping("/cashiers")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
    public ResponseEntity<ApiResponse<List<CashierDto>>> cashiers(@AuthenticationPrincipal User actor) {
        List<CashierDto> result = cashCutService.listCashiers(actor).stream()
                .map(row -> new CashierDto((Long) row[0], (String) row[1]))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(result, null));
    }

    /** Fila mínima {@code (id, nombre)} para el filtro "Cajero" — nunca expone el resto del {@link User}. */
    public record CashierDto(Long id, String name) {}

    // usado también por el widget del Dashboard, por eso admite ambas secciones
    /**
     * Obtiene el corte de caja actualmente abierto (si existe) del usuario
     * autenticado. Accesible desde la sección {@code CASH_CUTS} o desde el
     * {@code DASHBOARD}, ya que este dato también alimenta un widget del panel.
     *
     * @param actor usuario autenticado cuyo corte abierto se busca
     */
    @GetMapping("/open")
    @PreAuthorize("@sectionAccess.checkAny('CASH_CUTS', 'DASHBOARD')")
    public ResponseEntity<ApiResponse<CashCut>> getOpen(@AuthenticationPrincipal User actor) {
        Optional<CashCut> open = cashCutService.findOpen(actor);
        return ResponseEntity.ok(ApiResponse.ok(open.orElse(null), null));
    }

    // corte propio del día de hoy (abierto o ya cerrado) — para que quien lo cerró lo pueda seguir viendo
    /**
     * Obtiene el corte de caja del usuario autenticado correspondiente al día de
     * hoy, ya sea que siga abierto o que ya haya sido cerrado. Permite que quien
     * cerró su corte lo pueda seguir consultando el resto del día.
     *
     * @param actor usuario autenticado cuyo corte del día se busca
     */
    @GetMapping("/mine/today")
    @PreAuthorize("@sectionAccess.check('CASH_CUTS')")
    public ResponseEntity<ApiResponse<CashCut>> getMineToday(@AuthenticationPrincipal User actor) {
        Optional<CashCut> mine = cashCutService.findMineToday(actor);
        return ResponseEntity.ok(ApiResponse.ok(mine.orElse(null), null));
    }

    /**
     * Obtiene el detalle de un corte de caja por su id, sujeto al filtro de
     * tienda/propiedad aplicado por el service.
     *
     * @param id    identificador del corte
     * @param actor usuario autenticado
     */
    @GetMapping("/{id}")
    @PreAuthorize("@sectionAccess.check('CASH_CUTS')")
    public ResponseEntity<ApiResponse<CashCut>> get(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(cashCutService.findById(id, actor), null));
    }

    /**
     * Obtiene el resumen (totales por método de pago, diferencias, etc.) de un
     * corte de caja específico.
     *
     * @param id    identificador del corte
     * @param actor usuario autenticado
     */
    @GetMapping("/{id}/summary")
    @PreAuthorize("@sectionAccess.check('CASH_CUTS')")
    public ResponseEntity<ApiResponse<CashCutSummary>> summary(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(cashCutService.summary(id, actor), null));
    }

    /**
     * Abre un corte recibiendo una caja: el cajero indica cuánto efectivo contó y confirma
     * que está de acuerdo (ver {@link CashCutService#open}). Requiere {@code CASH_CUTS}.
     */
    @PostMapping("/open")
    @PreAuthorize("@sectionAccess.check('CASH_CUTS')")
    public ResponseEntity<ApiResponse<CashCut>> open(@Valid @RequestBody CashCutOpenRequest req,
                                                      @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(cashCutService.open(req, actor), "Corte abierto"));
    }

    /**
     * Cierra un corte con arqueo: el efectivo contado queda en la caja para el siguiente
     * turno (ver {@link CashCutService#close}).
     */
    @PostMapping("/{id}/close")
    @PreAuthorize("@sectionAccess.check('CASH_CUTS')")
    public ResponseEntity<ApiResponse<CashCut>> close(@PathVariable Long id,
                                                       @Valid @RequestBody CashCutCloseRequest req,
                                                       @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(cashCutService.close(id, req, actor), "Corte cerrado"));
    }

    /** Cortes con diferencia de arqueo sin resolver (contador de la pestaña Historial). */
    @GetMapping("/count-differences/unresolved-count")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
    public ResponseEntity<ApiResponse<Long>> unresolvedArqueoCount(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(cashCutService.unresolvedArqueoCount(actor), null));
    }

    /** Resuelve la diferencia del arqueo de cierre (faltante, sobrante o error de conteo). */
    @PostMapping("/{id}/resolve-count")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
    public ResponseEntity<ApiResponse<CashCut>> resolveCount(@PathVariable Long id,
                                                              @Valid @RequestBody ArqueoResolveRequest req,
                                                              @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(cashCutService.resolveArqueo(id, req, actor), "Diferencia resuelta"));
    }

    /**
     * Corte de fin de día de la caja: cierre con arqueo + reporte del día de esa caja a los
     * destinatarios de la tienda + la caja queda bloqueada hasta el job o el día siguiente.
     */
    @PostMapping("/{id}/end-of-day")
    @PreAuthorize("@sectionAccess.check('CASH_CUTS')")
    public ResponseEntity<ApiResponse<CashCut>> endOfDay(@PathVariable Long id,
                                                          @Valid @RequestBody CashCutCloseRequest req,
                                                          @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(cashCutService.endOfDay(id, req, actor), "Corte de fin de día realizado"));
    }

    /** Cierre forzado por un administrador, indicando cómo se contó el efectivo. */
    @PostMapping("/{id}/force-close")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
    public ResponseEntity<ApiResponse<CashCut>> forceClose(@PathVariable Long id,
                                                            @Valid @RequestBody CashCutForceCloseRequest req,
                                                            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(cashCutService.forceClose(id, req, actor), "Corte cerrado"));
    }

    /** Movimientos de efectivo (gastos, retiros, aportes...) registrados en un corte. */
    @GetMapping("/{id}/movements")
    @PreAuthorize("@sectionAccess.check('CASH_CUTS')")
    public ResponseEntity<ApiResponse<List<CashMovement>>> movements(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(cashCutService.movements(id, actor), null));
    }

    /** Movimientos con la caja cerrada que el corte vio al recibir la caja. */
    @GetMapping("/{id}/received-movements")
    @PreAuthorize("@sectionAccess.check('CASH_CUTS')")
    public ResponseEntity<ApiResponse<List<CashMovement>>> receivedMovements(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(cashCutService.receivedMovements(id, actor), null));
    }

    /**
     * Registra un movimiento de efectivo en un corte abierto. Lo hace el dueño del corte o
     * un administrador; los retiros, solo administradores (ver {@link CashMovementService#registerOnCut}).
     */
    @PostMapping("/{id}/movements")
    @PreAuthorize("@sectionAccess.check('CASH_CUTS')")
    public ResponseEntity<ApiResponse<CashMovement>> addMovement(@PathVariable Long id,
                                                                  @Valid @RequestBody CashMovementRequest req,
                                                                  @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(cashMovementService.registerOnCut(id, req, actor), "Movimiento registrado"));
    }

    /** Sube la foto de un comprobante; devuelve su ruta para mandarla en el movimiento/pago. */
    @PostMapping(value = "/receipts", consumes = "multipart/form-data")
    @PreAuthorize("@sectionAccess.check('CASH_CUTS')")
    public ResponseEntity<ApiResponse<String>> uploadReceipt(@RequestParam("file") MultipartFile file,
                                                              @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(cashMovementService.uploadReceipt(file, actor), "Comprobante cargado"));
    }
}
