package com.nexora.restopos.service;

import com.nexora.restopos.dto.ArqueoResolveRequest;
import com.nexora.restopos.dto.CashCutCloseRequest;
import com.nexora.restopos.dto.CashCutForceCloseRequest;
import com.nexora.restopos.dto.CashCutOpenRequest;
import com.nexora.restopos.dto.CashCutSummary;
import com.nexora.restopos.exception.LargeCashDifferenceException;
import com.nexora.restopos.model.CashCountMethod;
import com.nexora.restopos.model.CashCut;
import com.nexora.restopos.model.CashCutStatus;
import com.nexora.restopos.model.CashMovement;
import com.nexora.restopos.model.CashRegister;
import com.nexora.restopos.model.HandoverStatus;
import com.nexora.restopos.model.Shortage;
import com.nexora.restopos.model.ShortageOrigin;
import com.nexora.restopos.model.Tienda;
import com.nexora.restopos.model.User;
import com.nexora.restopos.repository.CashCutRepository;
import com.nexora.restopos.repository.CashMovementRepository;
import com.nexora.restopos.repository.CashRegisterRepository;
import com.nexora.restopos.repository.ShortageRepository;
import com.nexora.restopos.repository.UserRepository;
import com.nexora.restopos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Administra el ciclo de vida de los cortes de caja ({@link CashCut}): recepción de la caja
 * al abrir, consulta, resumen y cierre con arqueo (manual, forzado por un administrador o
 * automático).
 *
 * <p>Un corte es el turno de un cajero sobre una {@link CashRegister}. Cada usuario tiene a
 * lo más un corte abierto, y cada caja también; en una tienda con varias cajas puede haber
 * varios cortes abiertos a la vez (uno por caja). Al abrir, el cajero recibe lo que dejó el
 * corte anterior de esa caja (más/menos lo que un administrador haya movido con la caja
 * cerrada), lo cuenta y da su de acuerdo; si no coincide, queda una diferencia de entrega
 * para que un administrador la resuelva ({@link CashRegisterService#resolveHandover}). Al
 * cerrar se compara lo contado contra el efectivo esperado ({@link CashBalanceCalculator}) y
 * lo contado queda en la caja para el siguiente turno.</p>
 *
 * <p>El correo con el reporte de cierre NO se envía desde aquí: lo dispara el job una sola
 * vez al día por tienda, agrupando todos los cortes cerrados ese día (ver {@link
 * CashCutReportNotifier}).</p>
 */
@Service
@RequiredArgsConstructor
public class CashCutService {

    private static final String ADMIN = "ADMIN";
    private static final NumberFormat MONEY = NumberFormat.getCurrencyInstance(new Locale("es", "MX"));

    private final CashCutRepository cashCutRepository;
    private final CashRegisterRepository cashRegisterRepository;
    private final CashMovementRepository movementRepository;
    private final UserRepository userRepository;
    private final CashBalanceCalculator calculator;
    private final ShortageRepository shortageRepository;
    private final CashDayCloseService dayCloseService;
    private final CashCutReportNotifier reportNotifier;
    private final CashMovementService movementService;
    private final TenantScope tenantScope;

    /**
     * Obtiene el corte de caja actualmente abierto del actor, si tiene uno.
     *
     * @param actor usuario cuyo corte abierto se busca
     * @return el corte abierto del actor, o vacío si no tiene ninguno abierto
     */
    public Optional<CashCut> findOpen(User actor) {
        return cashCutRepository.findFirstByUserIdAndStatus(actor.getId(), CashCutStatus.OPEN);
    }

    // BETWEEN siempre necesita las dos fechas: Postgres no logra inferir el tipo de un
    // parámetro timestamp nulo (mismo caso que en SaleService.findAll), así que en vez de
    // null se manda un rango que cubre todo el historial cuando no se filtra por fecha.
    private static final LocalDateTime MIN_DATE = LocalDateTime.of(2000, 1, 1, 0, 0);
    private static final LocalDateTime MAX_DATE = LocalDateTime.of(2100, 1, 1, 0, 0);

    /**
     * Búsqueda paginada de cortes de caja con filtros combinables, acotada a la tienda
     * del actor.
     *
     * @param from fecha/hora mínima de apertura (inclusiva); si es null se usa un límite
     *             inferior muy antiguo para evitar pasar null al BETWEEN de la consulta
     * @param to fecha/hora máxima de apertura (inclusiva); si es null se usa un límite
     *           superior muy lejano, por la misma razón
     * @param status filtro por estado (abierto/cerrado), o null
     * @param userId filtra al cajero dueño del corte, o null para no filtrar
     * @param pageable paginación y orden solicitados
     * @param actor usuario que consulta; acota el resultado a su tienda
     * @return página de cortes que cumplen los filtros
     */
    public Page<CashCut> findAll(LocalDateTime from, LocalDateTime to, CashCutStatus status, Long userId,
                                 HandoverStatus arqueoStatus, Pageable pageable, User actor) {
        LocalDateTime effectiveFrom = from != null ? from : MIN_DATE;
        LocalDateTime effectiveTo = to != null ? to : MAX_DATE;
        return cashCutRepository.search(tenantScope.scopeId(actor), effectiveFrom, effectiveTo, status, userId, arqueoStatus, pageable);
    }

    /** Cortes con diferencia de arqueo todavía sin resolver, en la tienda del actor. */
    public long unresolvedArqueoCount(User actor) {
        return cashCutRepository.countUnresolvedArqueos(tenantScope.scopeId(actor));
    }

    /**
     * Resuelve la diferencia del arqueo de cierre de un corte (contado vs. esperado). La
     * diferencia nunca se borra: queda RESOLVED con su causa, quién y cuándo.
     * <ul>
     *   <li>Faltante confirmado: se genera un {@link Shortage} a cargo del responsable, que se
     *   cobra desde Faltantes.</li>
     *   <li>Sobrante confirmado: solo queda registrado (el dinero ya está en la caja).</li>
     *   <li>Error de conteo: el dinero sí cuadraba; si la caja sigue cerrada y este fue su
     *   último corte, su entrega pendiente (calculada con el conteo equivocado) se corrige.</li>
     * </ul>
     * Se resuelve sobre la diferencia efectiva ({@link CashCut#getEffectiveCashDifference}),
     * y solo después de resolver la diferencia al recibir la caja, porque el arqueo depende
     * de ese conteo.
     */
    @Transactional
    public CashCut resolveArqueo(Long id, ArqueoResolveRequest req, User actor) {
        if (!tenantScope.isAdminLevel(actor)) {
            throw new AccessDeniedException("Solo un administrador o supervisor puede resolver diferencias de arqueo");
        }
        CashCut cut = lockVisible(id, actor);
        if (cut.getArqueoStatus() != HandoverStatus.DIFFERENCE) {
            throw new IllegalStateException("Este corte no tiene una diferencia de arqueo pendiente");
        }
        if (cut.getHandoverStatus() == HandoverStatus.DIFFERENCE) {
            throw new IllegalStateException("Primero resuelve la diferencia al recibir la caja de este corte (pestaña Cajas): el arqueo depende de ese conteo");
        }
        BigDecimal diff = cut.getEffectiveCashDifference();
        switch (req.getResolution()) {
            case SHORTAGE -> {
                if (diff.signum() >= 0) throw new IllegalStateException("En este arqueo no faltó dinero");
                if (req.getResponsibleId() == null) throw new IllegalStateException("Indica quién es responsable del faltante");
                shortageRepository.save(Shortage.builder()
                        .tienda(cut.getTienda())
                        .person(movementService.findPerson(req.getResponsibleId(), cut.getTienda(), actor))
                        .amount(diff.abs())
                        .sourceCut(cut)
                        .origin(ShortageOrigin.CLOSE)
                        .reason(req.getNote().trim())
                        .createdBy(actor)
                        .build());
            }
            case SURPLUS -> {
                if (diff.signum() <= 0) throw new IllegalStateException("En este arqueo no sobró dinero");
            }
            case COUNT_ERROR -> {
                CashRegister register = cut.getCashRegister();
                if (register != null && diff.signum() != 0) {
                    CashRegister r = cashRegisterRepository.findByIdForUpdate(register.getId()).orElseThrow();
                    boolean stillClosed = cashCutRepository.findFirstByCashRegisterIdAndStatus(r.getId(), CashCutStatus.OPEN).isEmpty();
                    boolean isLastCut = r.getLastCut() != null && r.getLastCut().getId().equals(cut.getId());
                    if (stillClosed && isLastCut && r.getPendingAmount() != null) {
                        r.setPendingAmount(r.getPendingAmount().subtract(diff).max(BigDecimal.ZERO));
                        cashRegisterRepository.save(r);
                    }
                }
            }
        }
        cut.setArqueoStatus(HandoverStatus.RESOLVED);
        cut.setArqueoResolution(req.getResolution());
        cut.setArqueoResolvedBy(actor);
        cut.setArqueoResolvedAt(LocalDateTime.now());
        cut.setArqueoResolutionNote(req.getNote().trim());
        return cashCutRepository.save(cut);
    }

    /**
     * Cajeros distintos con al menos un corte de caja en la tienda del actor, para poblar
     * el filtro "Cajero" del historial (ver {@link CashCutRepository#findDistinctCashiers}).
     *
     * @param actor usuario que consulta; acota el resultado a su tienda
     * @return lista de {@code [id, nombre]} de cada cajero, ordenada por nombre
     */
    public List<Object[]> listCashiers(User actor) {
        return cashCutRepository.findDistinctCashiers(tenantScope.scopeId(actor));
    }

    // El corte propio del día de hoy, abierto o ya cerrado — a diferencia de findOpen(),
    // esto no depende de que siga OPEN, así que sigue disponible justo después de cerrarlo.
    /**
     * Obtiene el corte de caja propio del actor abierto hoy, sin importar si ya se cerró.
     *
     * @param actor usuario cuyo corte del día se busca
     * @return el corte del día del actor (abierto o cerrado), o vacío si no abrió ninguno hoy
     */
    public Optional<CashCut> findMineToday(User actor) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        return cashCutRepository.findFirstByUserIdAndOpenedAtBetweenOrderByOpenedAtDesc(
                actor.getId(), startOfDay, endOfDay);
    }

    /**
     * Busca un corte de caja por id, validando que el actor tenga permiso para verlo
     * (ver {@link #canView(CashCut, User)}).
     *
     * @param id id del corte
     * @param actor usuario que consulta
     * @return el corte encontrado
     * @throws IllegalArgumentException si no existe o el actor no tiene permiso para verlo
     */
    public CashCut findById(Long id, User actor) {
        CashCut cut = findById(id);
        if (!canView(cut, actor)) {
            throw new IllegalArgumentException("Corte no encontrado: " + id);
        }
        return cut;
    }

    private CashCut findById(Long id) {
        return cashCutRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Corte no encontrado: " + id));
    }

    /** Movimientos de efectivo registrados dentro de un corte. */
    public List<CashMovement> movements(Long id, User actor) {
        findById(id, actor);
        return movementRepository.findByCashCut_IdOrderByCreatedAtAsc(id);
    }

    /** Movimientos con la caja cerrada que vio este corte en el desglose de lo que recibió. */
    public List<CashMovement> receivedMovements(Long id, User actor) {
        findById(id, actor);
        return movementRepository.findByReceivedByCutIdOrderByCreatedAtAsc(id);
    }

    // ADMIN (o SUPERVISOR actuando sobre una de sus tiendas) ve cualquier corte de esa
    // tienda, para supervisar a todos sus cajeros a la vez; SUPER_ADMIN ve todo; el resto
    // solo ve el suyo propio: el abierto (aunque lo haya abierto ayer, p. ej. un turno de
    // noche) o el de hoy aunque ya esté cerrado.
    /**
     * Determina si el actor tiene permiso para ver un corte de caja en particular, según
     * la regla descrita en el comentario anterior.
     *
     * @param cut corte a validar
     * @param actor usuario que intenta verlo
     * @return true si el actor puede ver ese corte
     */
    private boolean canView(CashCut cut, User actor) {
        if (tenantScope.isSuperAdmin(actor)) return true;
        if (tenantScope.isSupervisor(actor) || (actor.getRole() != null && ADMIN.equals(actor.getRole().getName()))) {
            Long scope = tenantScope.scopeId(actor);
            return scope == null ? cut.getTienda() == null
                    : scope.equals(cut.getTienda() != null ? cut.getTienda().getId() : null);
        }
        return cut.getUser().getId().equals(actor.getId())
                && (cut.getStatus() == CashCutStatus.OPEN || cut.getOpenedAt().toLocalDate().equals(LocalDate.now()));
    }

    // ── Apertura: recibir la caja ───────────────────────────────────────────────────

    /**
     * Abre un corte recibiendo una caja: el fondo inicial es lo que el cajero contó
     * ({@code countedAmount}), y se compara contra lo que debía recibir (la entrega pendiente
     * de la caja). Si no coincide queda {@link HandoverStatus#DIFFERENCE} — la nota es
     * obligatoria — y un administrador la resuelve después; el corte se abre de todas formas
     * con lo contado, porque es lo que de verdad hay en la caja.
     *
     * <p>Reglas: el actor no puede tener otro corte abierto; la caja debe ser de su tienda,
     * estar activa y libre (sin otro corte abierto); y debe confirmar que contó y está de
     * acuerdo ({@code agreed}). La caja se bloquea mientras tanto, para que dos cajeros no
     * la abran a la vez.</p>
     *
     * @throws IllegalStateException si alguna regla no se cumple
     * @throws IllegalArgumentException si la caja no existe o no es de su tienda
     */
    @Transactional
    public CashCut open(CashCutOpenRequest req, User actor) {
        Tienda tienda = tenantScope.tiendaForWrite(actor);
        if (tienda == null) {
            throw new IllegalStateException("Elige una tienda para poder abrir un corte de caja");
        }
        if (findOpen(actor).isPresent()) {
            throw new IllegalStateException("Ya tienes un corte abierto. Ciérralo antes de abrir otro.");
        }
        CashRegister register = cashRegisterRepository.findByIdForUpdate(req.getCashRegisterId())
                .filter(r -> r.getTienda().getId().equals(tienda.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Caja no encontrada"));
        if (!Boolean.TRUE.equals(register.getIsActive())) {
            throw new IllegalStateException("La caja " + register.getName() + " está desactivada");
        }
        // Tras su corte de fin de día, la caja solo la vuelve a abrir el mismo día un
        // administrador o supervisor (el cajero espera al cierre programado o al día siguiente).
        if (CashDayCloseService.isDayClosed(register) && !tenantScope.isAdminLevel(actor)) {
            throw new IllegalStateException("La caja " + register.getName() + " ya hizo su corte de fin de día. "
                    + "Solo un administrador o supervisor puede volver a abrirla hoy.");
        }
        register.setDayClosed(false); // si se reabrió por cambio de día (respaldo del job), se limpia
        Optional<CashCut> busy = cashCutRepository.findFirstByCashRegisterIdAndStatus(register.getId(), CashCutStatus.OPEN);
        if (busy.isPresent()) {
            throw new IllegalStateException("La caja " + register.getName() + " ya está en uso por " + busy.get().getUser().getName());
        }
        if (!Boolean.TRUE.equals(req.getAgreed())) {
            throw new IllegalStateException("Confirma que contaste el efectivo y estás de acuerdo");
        }

        BigDecimal expected = register.getPendingAmount(); // null = la caja no tiene entrega previa
        BigDecimal counted = req.getCountedAmount();
        String note = blankToNull(req.getNote());
        HandoverStatus status = HandoverStatus.NONE;
        BigDecimal difference = null;
        if (expected != null && expected.signum() == 0) {
            // La caja se entrega vacía: lo que cuente es un fondo nuevo, no una diferencia.
            // Solo se pide confirmar el monto (volver a escribirlo) si es mayor a cero.
            if (counted.signum() > 0 && !Boolean.TRUE.equals(req.getLargeDifferenceConfirmed())) {
                throw new LargeCashDifferenceException("La caja se entrega vacía. Vuelve a escribir el fondo con el que empiezas para confirmarlo.");
            }
        } else if (expected != null) {
            if (CashCountRules.isUnreasonable(expected, counted)) {
                throw new IllegalStateException("El monto contado no es razonable para lo que debías recibir ("
                        + MONEY.format(expected) + "). Revisa que no sobren dígitos.");
            }
            if (CashCountRules.isLargeDifference(expected, counted) && !Boolean.TRUE.equals(req.getLargeDifferenceConfirmed())) {
                throw new LargeCashDifferenceException("La diferencia con lo que debías recibir es muy grande. Vuelve a escribir el monto para confirmarlo.");
            }
            difference = counted.subtract(expected);
            status = difference.signum() == 0 ? HandoverStatus.OK : HandoverStatus.DIFFERENCE;
            if (status == HandoverStatus.DIFFERENCE && note == null) {
                throw new IllegalStateException("Lo que contaste no coincide con lo que debías recibir: escribe una nota");
            }
        }

        CashCut cut = new CashCut();
        cut.setUser(actor);
        cut.setTienda(tienda);
        cut.setCashRegister(register);
        cut.setOpeningAmount(counted);
        cut.setStatus(CashCutStatus.OPEN);
        cut.setPreviousCut(register.getLastCut());
        cut.setExpectedOpeningAmount(expected);
        cut.setReceivedCountMethod(register.getPendingCountMethod());
        cut.setReceiveNote(note);
        cut.setHandoverStatus(status);
        cut.setHandoverDifference(difference);
        CashCut saved = cashCutRepository.save(cut);

        // Los movimientos con la caja cerrada quedan como "los que vio este corte al recibir".
        for (CashMovement m : movementRepository.findByCashRegisterIdAndCashCutIsNullAndReceivedByCutIsNullOrderByCreatedAtAsc(register.getId())) {
            m.setReceivedByCut(saved);
            movementRepository.save(m);
        }
        // Mientras hay un corte abierto, la caja no tiene entrega pendiente.
        register.setPendingAmount(null);
        register.setPendingCountMethod(null);
        register.setPendingCountedBy(null);
        register.setPendingCountedAt(null);
        cashRegisterRepository.save(register);
        return saved;
    }

    /**
     * Calcula el resumen en vivo de un corte (fondo inicial, ventas por método de pago,
     * cancelaciones, movimientos de efectivo y efectivo esperado) — sirve tanto para un
     * corte todavía abierto como para uno ya cerrado.
     *
     * @throws IllegalArgumentException si el corte no existe o el actor no tiene permiso para verlo
     */
    public CashCutSummary summary(Long id, User actor) {
        CashCut cut = findById(id, actor);
        CashCutSummary summary = calculator.summarize(cut);
        // Conteo ciego: mientras el corte sigue abierto, el cajero no ve su efectivo esperado
        // ni nada de lo que se pueda despejar (ventas en efectivo y total de ventas).
        if (cut.getStatus() == CashCutStatus.OPEN && isBlindFor(cut, actor)) {
            summary.setExpectedCash(null);
            summary.setCashSales(null);
            summary.setTotalSales(null);
        }
        return summary;
    }

    /**
     * {@code true} si el corte está en una tienda con conteo ciego y quien consulta/opera es
     * un cajero (no ADMIN/SUPERVISOR/SUPER_ADMIN, que siempre ven todo).
     */
    boolean isBlindFor(CashCut cut, User actor) {
        return cut.getTienda() != null && Boolean.TRUE.equals(cut.getTienda().getBlindCashCount())
                && !tenantScope.isAdminLevel(actor);
    }

    // ── Cierre ────────────────────────────────────────────────────────────────────

    // El aviso por correo NO se manda aquí: solo lo dispara el job programado
    // (CashCutAutoCloseJob), que junta en un solo reporte por tienda TODOS los
    // cortes del día — los cerrados a mano en cualquier momento y los que él mismo
    // cierra al llegar la hora configurada.
    /**
     * Cierra un corte con arqueo: {@code countedAmount} es el efectivo que hay de verdad en
     * la caja, y queda en ella para el siguiente turno. Si no cuadra con el esperado, la nota
     * es obligatoria. Lo cierra su propio cajero (conteo {@code CASHIER}) o un administrador
     * presente (conteo {@code ADMIN_IN_PERSON}).
     *
     * @throws IllegalStateException si el corte ya estaba cerrado o falta la nota
     * @throws IllegalArgumentException si el corte no existe o el actor no tiene permiso
     */
    /**
     * Corte de fin de día: el mismo cierre con arqueo que un traspaso ({@link #close}), pero
     * además manda el reporte de los cortes del día de esa caja (un correo, un Excel) a los
     * destinatarios de la tienda y bloquea la caja hasta que el job la reabra (o hasta el día
     * siguiente). El efectivo contado se queda en la caja para quien la abra después.
     */
    @Transactional
    public CashCut endOfDay(Long id, CashCutCloseRequest req, User actor) {
        CashCut closed = close(id, req, actor);
        if (closed.getCashRegister() != null) {
            dayCloseService.finishDay(closed.getCashRegister().getId());
        } else {
            reportNotifier.notify(List.of(closed)); // corte anterior a las cajas: solo su reporte
        }
        return closed;
    }

    @Transactional
    public CashCut close(Long id, CashCutCloseRequest req, User actor) {
        CashCut cut = lockVisible(id, actor);
        ensureOpen(cut);
        boolean own = cut.getUser().getId().equals(actor.getId());
        CashCutSummary summary = calculator.summarize(cut);
        BigDecimal expected = summary.getExpectedCash();
        BigDecimal counted = req.getCountedAmount();
        String notes = blankToNull(req.getNotes());
        // En conteo ciego los mensajes no revelan el esperado (el cajero lo ve hasta cerrar).
        boolean blind = isBlindFor(cut, actor);
        if (CashCountRules.isUnreasonable(expected, counted)) {
            throw new IllegalStateException(blind
                    ? "El monto contado no es razonable para esta caja. Revisa que no sobren dígitos."
                    : "El monto contado no es razonable: el efectivo esperado es " + MONEY.format(expected) + ". Revisa que no sobren dígitos.");
        }
        if (CashCountRules.isLargeDifference(expected, counted) && !Boolean.TRUE.equals(req.getLargeDifferenceConfirmed())) {
            throw new LargeCashDifferenceException(blind
                    ? "El monto es muy distinto a lo esperado. Vuelve a escribirlo para confirmarlo."
                    : "La diferencia con el efectivo esperado es muy grande. Vuelve a escribir el monto para confirmarlo.");
        }
        // Con conteo ciego el cajero no sabe si cuadró hasta después de cerrar: la nota no se le exige.
        if (!blind && counted.compareTo(expected) != 0 && notes == null) {
            throw new IllegalStateException("El efectivo contado no cuadra con el esperado: escribe una nota");
        }
        return closeInternal(cut, summary, counted, own ? CashCountMethod.CASHIER : CashCountMethod.ADMIN_IN_PERSON,
                actor, null, notes, actor);
    }

    /**
     * Cierre forzado por un administrador (p. ej. el cajero se fue sin cerrar), indicando
     * cómo se contó el efectivo: en persona (lo contó el admin), a distancia (lo reportó el
     * cajero: queda a su nombre, con evidencia opcional) o sin contar (se entrega el esperado).
     *
     * @throws AccessDeniedException si el actor no es administrador/supervisor
     * @throws IllegalStateException si el corte ya estaba cerrado o faltan datos del conteo
     */
    @Transactional
    public CashCut forceClose(Long id, CashCutForceCloseRequest req, User actor) {
        if (!tenantScope.isAdminLevel(actor)) {
            throw new AccessDeniedException("Solo un administrador o supervisor puede forzar el cierre de un corte");
        }
        CashCut cut = lockVisible(id, actor);
        ensureOpen(cut);
        CashCutSummary summary = calculator.summarize(cut);

        BigDecimal counted = null;
        User countedBy = null;
        String evidence = null;
        switch (req.getCountMethod()) {
            case ADMIN_IN_PERSON -> {
                counted = requireCounted(req.getCountedAmount());
                countedBy = actor;
            }
            case REMOTE_REPORTED -> {
                counted = requireCounted(req.getCountedAmount());
                if (req.getReportedById() == null) throw new IllegalStateException("Indica quién reportó el conteo");
                countedBy = userRepository.findById(req.getReportedById())
                        .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
                evidence = blankToNull(req.getEvidencePath());
            }
            case NOT_COUNTED -> { /* se entrega el efectivo esperado */ }
            default -> throw new IllegalArgumentException("Método de conteo no válido para un cierre forzado");
        }
        if (counted != null && CashCountRules.isUnreasonable(summary.getExpectedCash(), counted)) {
            throw new IllegalStateException("El monto contado no es razonable: el efectivo esperado es "
                    + MONEY.format(summary.getExpectedCash()) + ". Revisa que no sobren dígitos.");
        }
        String notes = "Cierre forzado por " + actor.getName() + ": " + req.getReason().trim();
        return closeInternal(cut, summary, counted, req.getCountMethod(), countedBy, evidence, notes, actor);
    }

    // Usado por el job programado de cierre automático: nadie estuvo ahí para contar, así
    // que se entrega el efectivo esperado (NOT_COUNTED) y closedBy queda null a propósito
    // para que quede registrado que lo cerró el sistema, no un usuario.
    /**
     * Cierra automáticamente un corte que sigue abierto al llegar la hora configurada,
     * llamado exclusivamente por {@code CashCutAutoCloseJob}.
     *
     * @throws IllegalStateException si el corte ya estaba cerrado
     * @throws IllegalArgumentException si el corte no existe
     */
    @Transactional
    public CashCut autoClose(Long id) {
        CashCut cut = cashCutRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Corte no encontrado: " + id));
        ensureOpen(cut);
        return closeInternal(cut, calculator.summarize(cut), null, CashCountMethod.NOT_COUNTED, null, null,
                "Cerrado automáticamente por el sistema (corte del día).", null);
    }

    private CashCut lockVisible(Long id, User actor) {
        findById(id, actor); // valida permiso
        return cashCutRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Corte no encontrado: " + id));
    }

    /**
     * Valida que un corte siga abierto antes de intentar cerrarlo.
     *
     * @throws IllegalStateException si el corte ya está cerrado
     */
    private void ensureOpen(CashCut cut) {
        if (cut.getStatus() != CashCutStatus.OPEN) {
            throw new IllegalStateException("El corte ya está cerrado");
        }
    }

    private static BigDecimal requireCounted(BigDecimal counted) {
        if (counted == null) throw new IllegalStateException("Captura cuánto efectivo se contó");
        return counted;
    }

    /**
     * Lógica común de cierre: consolida los totales del corte, registra el arqueo
     * ({@code countedAmount} contra el esperado) y deja en la caja lo contado — o el esperado,
     * si nadie contó — como entrega pendiente para el siguiente turno.
     *
     * @param counted efectivo contado, o null si nadie contó
     * @param closedBy usuario que cierra, o null si lo cerró el sistema (cierre automático)
     */
    private CashCut closeInternal(CashCut cut, CashCutSummary summary, BigDecimal counted, CashCountMethod method,
                                  User countedBy, String evidencePath, String notes, User closedBy) {
        BigDecimal expected = summary.getExpectedCash();
        BigDecimal handover = counted != null ? counted : expected.max(BigDecimal.ZERO);
        LocalDateTime now = LocalDateTime.now();

        cut.setTotalSales(summary.getTotalSales());
        cut.setCashSales(summary.getCashSales());
        cut.setCardSales(summary.getCardSales());
        cut.setTransferSales(summary.getTransferSales());
        cut.setTotalTransactions(summary.getTotalTransactions());
        cut.setCancelledCount(summary.getCancelledCount());
        cut.setCancelledTotal(summary.getCancelledTotal());
        cut.setExpenses(summary.getExpensesTotal());
        cut.setEntriesTotal(summary.getEntriesTotal());
        cut.setWithdrawalsTotal(summary.getWithdrawalsTotal());
        cut.setReimbursementsTotal(summary.getReimbursementsTotal());
        cut.setLoansOutTotal(summary.getLoansOutTotal());
        cut.setClosingAmount(expected);
        cut.setCountedAmount(counted);
        cut.setCashDifference(counted != null ? counted.subtract(expected) : null);
        cut.setCountMethod(method);
        cut.setCountedBy(countedBy);
        cut.setCountEvidencePath(evidencePath);
        cut.setHandoverAmount(handover);
        cut.setArqueoStatus(counted == null ? HandoverStatus.NONE
                : counted.compareTo(expected) == 0 ? HandoverStatus.OK : HandoverStatus.DIFFERENCE);
        cut.setStatus(CashCutStatus.CLOSED);
        cut.setClosedAt(now);
        cut.setClosedBy(closedBy);
        if (notes != null) cut.setNotes(notes);
        CashCut saved = cashCutRepository.save(cut);

        if (cut.getCashRegister() != null) {
            CashRegister register = cashRegisterRepository.findByIdForUpdate(cut.getCashRegister().getId())
                    .orElseThrow(() -> new IllegalStateException("Caja no encontrada"));
            register.setPendingAmount(handover);
            register.setPendingCountMethod(method);
            register.setPendingCountedBy(countedBy);
            register.setPendingCountedAt(now);
            register.setLastCut(saved);
            cashRegisterRepository.save(register);
        }
        return saved;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
