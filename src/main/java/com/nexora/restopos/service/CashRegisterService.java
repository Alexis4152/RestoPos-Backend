package com.nexora.restopos.service;

import com.nexora.restopos.dto.CashRegisterRequest;
import com.nexora.restopos.dto.CashRegisterStatusDto;
import com.nexora.restopos.dto.HandoverDto;
import com.nexora.restopos.dto.HandoverResolveRequest;
import com.nexora.restopos.model.CashCut;
import com.nexora.restopos.model.CashCutStatus;
import com.nexora.restopos.model.CashMovement;
import com.nexora.restopos.model.CashMovementType;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Cajas de una tienda ({@link CashRegister}): su estado (en uso o cerrada con su entrega
 * pendiente), alta/edición, las entregas entre turnos y la resolución de diferencias.
 *
 * <p>Toda tienda tiene al menos una caja: si no tiene ninguna, la primera consulta crea
 * "Caja 1" — así las tiendas que ya existían antes de las cajas siguen funcionando sin
 * configurar nada.</p>
 */
@Service
@RequiredArgsConstructor
public class CashRegisterService {

    private final CashRegisterRepository registerRepository;
    private final CashCutRepository cashCutRepository;
    private final CashMovementRepository movementRepository;
    private final UserRepository userRepository;
    private final CashBalanceCalculator calculator;
    private final CashMovementService movementService;
    private final ShortageRepository shortageRepository;
    private final TenantScope tenantScope;

    /** Cajas de la tienda del actor con su estado (en uso o cerrada con su entrega pendiente). */
    @Transactional
    public List<CashRegisterStatusDto> listStatus(User actor) {
        Tienda tienda = requireTienda(actor);
        List<CashRegister> registers = registerRepository.findByTiendaIdOrderByIdAsc(tienda.getId());
        if (registers.isEmpty()) {
            registers = List.of(registerRepository.save(CashRegister.builder()
                    .tienda(tienda).name("Caja 1").isActive(true).build()));
        }
        boolean admin = tenantScope.isAdminLevel(actor);
        return registers.stream().map(r -> toStatus(r, admin)).toList();
    }

    // El efectivo esperado de una caja en uso solo lo ve un administrador: a un cajero no le
    // sirve (no es su caja) y en conteo ciego revelaría lo que no debe ver.
    private CashRegisterStatusDto toStatus(CashRegister r, boolean admin) {
        Optional<CashCut> open = cashCutRepository.findFirstByCashRegisterIdAndStatus(r.getId(), CashCutStatus.OPEN);
        if (open.isPresent()) {
            CashCut c = open.get();
            return new CashRegisterStatusDto(r.getId(), r.getName(), Boolean.TRUE.equals(r.getIsActive()),
                    new CashRegisterStatusDto.OpenCut(c.getId(), c.getUser().getId(), c.getUser().getName(),
                            c.getOpenedAt(), admin ? calculator.expectedCash(c) : null),
                    null, false, r.getLastDayCloseAt());
        }
        CashCut last = r.getLastCut();
        CashRegisterStatusDto.Pending pending = new CashRegisterStatusDto.Pending(
                r.getPendingAmount(),
                r.getPendingCountMethod(),
                name(r.getPendingCountedBy()),
                r.getPendingCountedAt(),
                last != null ? last.getId() : null,
                last != null ? name(last.getUser()) : null,
                last != null ? last.getClosedAt() : null,
                last != null ? last.getHandoverAmount() : null,
                last != null ? last.getCountMethod() : null,
                last != null ? name(last.getCountedBy()) : null,
                movementRepository.findByCashRegisterIdAndCashCutIsNullAndReceivedByCutIsNullOrderByCreatedAtAsc(r.getId()));
        return new CashRegisterStatusDto(r.getId(), r.getName(), Boolean.TRUE.equals(r.getIsActive()), null, pending,
                CashDayCloseService.isDayClosed(r), r.getLastDayCloseAt());
    }

    /** Da de alta una caja nueva en la tienda del actor. */
    @Transactional
    public CashRegister create(CashRegisterRequest req, User actor) {
        requireAdmin(actor);
        Tienda tienda = requireTienda(actor);
        String name = req.getName().trim();
        ensureUniqueName(tienda.getId(), name, null);
        return registerRepository.save(CashRegister.builder().tienda(tienda).name(name).isActive(true).build());
    }

    /**
     * Reabre una caja que ya hizo su corte de fin de día, para que cualquiera pueda volver a
     * abrir corte en ella hoy (solo administrador/supervisor). No reenvía ni borra el reporte
     * ya enviado: los cortes que se hagan después irán en su siguiente fin de día.
     */
    @Transactional
    public CashRegister reopenDay(Long id, User actor) {
        requireAdmin(actor);
        Tienda tienda = requireTienda(actor);
        CashRegister r = registerRepository.findByIdForUpdate(id)
                .filter(x -> x.getTienda().getId().equals(tienda.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Caja no encontrada"));
        if (!CashDayCloseService.isDayClosed(r)) {
            throw new IllegalStateException("La caja " + r.getName() + " no está cerrada por fin de día");
        }
        r.setDayClosed(false);
        return registerRepository.save(r);
    }

    /** Renombra o activa/desactiva una caja. Una caja en uso no se puede desactivar. */
    @Transactional
    public CashRegister update(Long id, CashRegisterRequest req, User actor) {
        requireAdmin(actor);
        Tienda tienda = requireTienda(actor);
        CashRegister r = registerRepository.findById(id)
                .filter(x -> x.getTienda().getId().equals(tienda.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Caja no encontrada"));
        String name = req.getName().trim();
        ensureUniqueName(tienda.getId(), name, r.getId());
        r.setName(name);
        if (req.getIsActive() != null) {
            if (!req.getIsActive() && cashCutRepository.findFirstByCashRegisterIdAndStatus(r.getId(), CashCutStatus.OPEN).isPresent()) {
                throw new IllegalStateException("No se puede desactivar una caja en uso");
            }
            r.setIsActive(req.getIsActive());
        }
        return registerRepository.save(r);
    }

    private void ensureUniqueName(Long tiendaId, String name, Long excludeId) {
        boolean taken = registerRepository.findByTiendaIdOrderByIdAsc(tiendaId).stream()
                .anyMatch(r -> !r.getId().equals(excludeId) && r.getName().equalsIgnoreCase(name));
        if (taken) throw new IllegalStateException("Ya existe una caja con ese nombre");
    }

    /** Personal activo de la tienda (y el propio actor), para los selects de quién puso/retiró dinero. */
    public List<PersonDto> people(User actor) {
        Tienda tienda = requireTienda(actor);
        List<PersonDto> result = new ArrayList<>(userRepository.findByTiendaIdAndIsActiveTrueOrderByNameAsc(tienda.getId())
                .stream().map(u -> new PersonDto(u.getId(), u.getName(), roleName(u))).toList());
        if (result.stream().noneMatch(p -> p.id().equals(actor.getId()))) {
            result.add(0, new PersonDto(actor.getId(), actor.getName(), roleName(actor)));
        }
        return result;
    }

    /** {@code role}: nombre del rol (ADMIN, CASHIER, SUPERVISOR, uno personalizado...) para mostrarlo junto al nombre. */
    public record PersonDto(Long id, String name, String role) {}

    private static String roleName(User u) {
        return u.getRole() != null ? u.getRole().getName() : null;
    }

    // ── Entregas entre turnos ──────────────────────────────────────────────────────

    /** "Últimas entregas de caja" de la tienda del actor. */
    public Page<HandoverDto> handovers(Pageable pageable, User actor) {
        return cashCutRepository.findHandovers(tenantScope.scopeId(actor), pageable).map(this::toHandover);
    }

    /** Entregas con diferencia todavía sin resolver. */
    public long unresolvedCount(User actor) {
        return cashCutRepository.countUnresolvedHandovers(tenantScope.scopeId(actor));
    }

    /**
     * Resuelve una diferencia de entrega. La diferencia nunca se borra: queda
     * {@link HandoverStatus#RESOLVED} con su causa, quién la resolvió y cuándo.
     * <ul>
     *   <li>Retiro no registrado: se registra el retiro con su fecha real, marcado como
     *   registrado después, entre los movimientos con la caja cerrada de esa entrega.</li>
     *   <li>Error de conteo: el dinero sí estaba; si el corte que recibió sigue abierto, su
     *   fondo inicial pasa a ser lo que debía recibir.</li>
     *   <li>Faltante confirmado: solo queda registrado.</li>
     * </ul>
     */
    @Transactional
    public HandoverDto resolveHandover(Long cutId, HandoverResolveRequest req, User actor) {
        requireAdmin(actor);
        Long scope = tenantScope.scopeId(actor);
        CashCut cut = cashCutRepository.findByIdForUpdate(cutId)
                .filter(c -> scope == null ? tenantScope.isSuperAdmin(actor)
                        : c.getTienda() != null && scope.equals(c.getTienda().getId()))
                .orElseThrow(() -> new IllegalArgumentException("Entrega no encontrada"));
        if (cut.getHandoverStatus() != HandoverStatus.DIFFERENCE) {
            throw new IllegalStateException("Esta entrega no tiene una diferencia pendiente");
        }

        switch (req.getResolution()) {
            case UNREGISTERED_WITHDRAWAL -> {
                if (req.getAmount() == null) throw new IllegalStateException("Captura el monto retirado");
                if (req.getDestination() == null) throw new IllegalStateException("Indica el destino del dinero");
                CashMovement m = new CashMovement();
                m.setTienda(cut.getTienda());
                m.setCashRegister(cut.getCashRegister());
                m.setReceivedByCut(cut);
                m.setType(CashMovementType.WITHDRAWAL);
                m.setAmount(req.getAmount());
                m.setConcept("Retiro registrado después");
                m.setPerson(req.getPersonId() != null
                        ? movementService.findPerson(req.getPersonId(), cut.getTienda(), actor) : actor);
                m.setDestination(req.getDestination());
                m.setRegisteredBy(actor);
                m.setOccurredAt(req.getOccurredAt() != null ? req.getOccurredAt() : LocalDateTime.now());
                m.setLateRegistered(true);
                movementRepository.save(m);
            }
            case COUNT_ERROR -> {
                if (cut.getStatus() == CashCutStatus.OPEN && cut.getExpectedOpeningAmount() != null) {
                    cut.setOpeningAmount(cut.getExpectedOpeningAmount());
                }
            }
            case SHORTAGE -> {
                // Un faltante (diferencia negativa) queda a cargo de una persona, que lo va
                // cubriendo desde la pestaña Faltantes. Un sobrante solo queda registrado.
                if (cut.getHandoverDifference() != null && cut.getHandoverDifference().signum() < 0) {
                    if (req.getResponsibleId() == null) throw new IllegalStateException("Indica quién es responsable del faltante");
                    shortageRepository.save(Shortage.builder()
                            .tienda(cut.getTienda())
                            .person(movementService.findPerson(req.getResponsibleId(), cut.getTienda(), actor))
                            .amount(cut.getHandoverDifference().abs())
                            .sourceCut(cut)
                            .reason(req.getNote().trim())
                            .createdBy(actor)
                            .build());
                }
            }
        }

        cut.setHandoverStatus(HandoverStatus.RESOLVED);
        cut.setHandoverResolution(req.getResolution());
        cut.setHandoverResolvedBy(actor);
        cut.setHandoverResolvedAt(LocalDateTime.now());
        cut.setHandoverResolutionNote(req.getNote().trim());
        return toHandover(cashCutRepository.save(cut));
    }

    private HandoverDto toHandover(CashCut c) {
        CashCut prev = c.getPreviousCut();
        Optional<Shortage> shortage = shortageRepository.findFirstBySourceCut_IdAndOrigin(c.getId(), ShortageOrigin.HANDOVER);
        return new HandoverDto(
                c.getId(),
                c.getCashRegister() != null ? c.getCashRegister().getName() : null,
                prev != null ? prev.getId() : null,
                prev != null && prev.getUser() != null ? prev.getUser().getId() : null,
                prev != null ? name(prev.getUser()) : null,
                prev != null ? prev.getClosedAt() : null,
                name(c.getUser()),
                c.getOpenedAt(),
                c.getExpectedOpeningAmount(),
                // Lo que contó quien recibió: no se toma de openingAmount porque un "error de
                // conteo" resuelto lo corrige después, y aquí debe verse el conteo original.
                c.getExpectedOpeningAmount() != null && c.getHandoverDifference() != null
                        ? c.getExpectedOpeningAmount().add(c.getHandoverDifference())
                        : c.getOpeningAmount(),
                c.getHandoverDifference(),
                c.getHandoverStatus(),
                c.getReceivedCountMethod(),
                c.getReceiveNote(),
                c.getHandoverResolution(),
                name(c.getHandoverResolvedBy()),
                c.getHandoverResolvedAt(),
                c.getHandoverResolutionNote(),
                movementRepository.findByReceivedByCutIdOrderByCreatedAtAsc(c.getId()),
                shortage.map(Shortage::getId).orElse(null),
                shortage.map(s -> name(s.getPerson())).orElse(null));
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private void requireAdmin(User actor) {
        if (!tenantScope.isAdminLevel(actor)) {
            throw new AccessDeniedException("Solo un administrador o supervisor puede hacer esto");
        }
    }

    private Tienda requireTienda(User actor) {
        Tienda tienda = tenantScope.tiendaForWrite(actor);
        if (tienda == null) throw new IllegalStateException("Elige una tienda para continuar");
        return tienda;
    }

    private static String name(User u) {
        return u != null ? u.getName() : null;
    }
}
