package com.nexora.restopos.controller;

import com.nexora.restopos.dto.ApiResponse;
import com.nexora.restopos.dto.CashCutRequest;
import com.nexora.restopos.dto.CashCutSummary;
import com.nexora.restopos.dto.MovimientoCajaRequest;
import com.nexora.restopos.dto.PageResponse;
import com.nexora.restopos.model.CashCut;
import com.nexora.restopos.model.CashCutStatus;
import com.nexora.restopos.model.MovimientoCaja;
import com.nexora.restopos.model.User;
import com.nexora.restopos.service.CashCutService;
import com.nexora.restopos.service.MovimientoCajaService;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Controlador de cortes de caja ("CashCut"), expuesto bajo {@code /api/cash-cuts}.
 * <p>
 * Cada cajero/vendedor abre y cierra su propio corte diario (efectivo esperado
 * vs. contado); el historial completo en forma de tabla solo lo puede consultar
 * un ADMIN (de su propia tienda) o SUPER_ADMIN (de todas). El resto de las
 * operaciones sobre el corte propio están protegidas por la sección dinámica
 * {@code CASH_CUTS} (o {@code DASHBOARD} cuando el dato se reutiliza en un widget).
 */
@RestController
@RequestMapping("/api/cash-cuts")
@RequiredArgsConstructor
public class CashCutController {

    private final CashCutService cashCutService;
    private final MovimientoCajaService movimientoCajaService;

    // tabla/historial de cortes: solo ADMIN (de su propia tienda) o SUPER_ADMIN (todas)
    /**
     * Lista paginada del historial de cortes de caja, con filtros opcionales por
     * rango de fechas y estado. Solo accesible para ADMIN (limitado a su tienda)
     * o SUPER_ADMIN (todas las tiendas).
     *
     * @param from   fecha/hora inicial del rango (opcional)
     * @param to     fecha/hora final del rango (opcional)
     * @param status estado del corte a filtrar (opcional)
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
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User actor) {
        Pageable pageable = PageRequest.of(page, size);
        Page<CashCut> result = cashCutService.findAll(from, to, status, pageable, actor);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(result), null));
    }

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
     * Abre un nuevo corte de caja para el usuario autenticado (normalmente con
     * el fondo/efectivo inicial). Requiere acceso a la sección {@code CASH_CUTS}.
     *
     * @param req   datos de apertura del corte
     * @param actor usuario autenticado que abre el corte
     */
    @PostMapping("/open")
    @PreAuthorize("@sectionAccess.check('CASH_CUTS')")
    public ResponseEntity<ApiResponse<CashCut>> open(@Valid @RequestBody CashCutRequest req,
                                                      @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(cashCutService.open(req, actor), "Corte abierto"));
    }

    /**
     * Cierra un corte de caja existente, registrando el efectivo/valores contados
     * al final del turno.
     *
     * @param id    identificador del corte a cerrar
     * @param req   datos de cierre (efectivo contado, notas, etc.)
     * @param actor usuario autenticado que cierra el corte
     */
    @PostMapping("/{id}/close")
    @PreAuthorize("@sectionAccess.check('CASH_CUTS')")
    public ResponseEntity<ApiResponse<CashCut>> close(@PathVariable Long id,
                                                       @RequestBody CashCutRequest req,
                                                       @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(cashCutService.close(id, req, actor), "Corte cerrado"));
    }

    /** Registra una entrada/salida/ajuste manual de efectivo sobre el corte abierto del actor. */
    @PostMapping("/movements")
    @PreAuthorize("@sectionAccess.check('CASH_CUTS')")
    public ResponseEntity<ApiResponse<MovimientoCaja>> registrarMovimiento(@Valid @RequestBody MovimientoCajaRequest req,
                                                                            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(movimientoCajaService.registrar(req, actor), "Movimiento registrado"));
    }

    /** Lista los movimientos manuales de efectivo de un corte específico. */
    @GetMapping("/{id}/movements")
    @PreAuthorize("@sectionAccess.check('CASH_CUTS')")
    public ResponseEntity<ApiResponse<List<MovimientoCaja>>> movimientos(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        cashCutService.findById(id, actor); // valida acceso al corte
        return ResponseEntity.ok(ApiResponse.ok(movimientoCajaService.listByCashCut(id), null));
    }
}
