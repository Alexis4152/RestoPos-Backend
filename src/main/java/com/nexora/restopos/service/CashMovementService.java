package com.nexora.restopos.service;

import com.nexora.restopos.dto.CashMovementRequest;
import com.nexora.restopos.model.CashCountMethod;
import com.nexora.restopos.model.CashCut;
import com.nexora.restopos.model.CashCutStatus;
import com.nexora.restopos.model.CashMovement;
import com.nexora.restopos.model.CashMovementType;
import com.nexora.restopos.model.CashRegister;
import com.nexora.restopos.model.Reimbursement;
import com.nexora.restopos.model.ReimbursementPaymentSource;
import com.nexora.restopos.model.ReimbursementStatus;
import com.nexora.restopos.model.Tienda;
import com.nexora.restopos.model.User;
import com.nexora.restopos.repository.CashCutRepository;
import com.nexora.restopos.repository.CashMovementRepository;
import com.nexora.restopos.repository.CashRegisterRepository;
import com.nexora.restopos.repository.ReimbursementRepository;
import com.nexora.restopos.repository.UserRepository;
import com.nexora.restopos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Registra los movimientos de efectivo de las cajas que no son ventas ({@link CashMovement}):
 * dentro de un corte abierto (gasto, retiro, pago de reembolso, aporte, entrada de cambio) o
 * con la caja cerrada (solo retiro o entrada, y solo administradores).
 *
 * <p>Regla central: una salida nunca puede sacar más efectivo del que hay. Dentro de un
 * corte, el disponible es su efectivo esperado ({@link CashBalanceCalculator}); con la caja
 * cerrada, su entrega pendiente. Si alguien pagó con su propio dinero, primero se registra
 * su aporte (que genera un {@link Reimbursement}) y luego la salida completa.</p>
 */
@Service
@RequiredArgsConstructor
public class CashMovementService {

    private static final long MAX_RECEIPT_BYTES = 5L * 1024 * 1024; // 5 MB
    private static final NumberFormat MONEY = NumberFormat.getCurrencyInstance(new Locale("es", "MX"));

    private final CashCutRepository cashCutRepository;
    private final CashRegisterRepository cashRegisterRepository;
    private final CashMovementRepository movementRepository;
    private final ReimbursementRepository reimbursementRepository;
    private final UserRepository userRepository;
    private final CashBalanceCalculator calculator;
    private final ImageStorageService imageStorageService;
    private final TenantScope tenantScope;

    /**
     * Registra un movimiento dentro de un corte abierto. Lo puede hacer el dueño del corte o
     * un administrador/supervisor de esa tienda; los retiros, solo estos últimos.
     *
     * @throws IllegalStateException si el corte está cerrado, faltan datos o la salida excede el efectivo disponible
     * @throws AccessDeniedException si el actor no puede operar ese corte o ese tipo de movimiento
     */
    @Transactional
    public CashMovement registerOnCut(Long cutId, CashMovementRequest req, User actor) {
        CashCut cut = lockOpenCut(cutId, actor);
        CashMovementType type = req.getType();
        if (type == CashMovementType.WITHDRAWAL && !tenantScope.isAdminLevel(actor)) {
            throw new AccessDeniedException("Solo un administrador o supervisor puede registrar retiros");
        }
        if (type == CashMovementType.REIMBURSEMENT) {
            if (req.getReimbursementId() == null) throw new IllegalStateException("Elige el reembolso a pagar");
            return payReimbursement(cut, req.getReimbursementId(), req.getReceiptPath(), actor);
        }

        BigDecimal amount = requireAmount(req.getAmount());
        if (type.isOutflow()) ensureAvailable(calculator.expectedCash(cut), amount, isBlind(cut, actor));

        CashMovement m = newMovement(cut.getTienda(), cut.getCashRegister(), type, amount, actor);
        m.setCashCut(cut);
        applyDetails(m, req, cut.getTienda(), actor);
        CashMovement saved = movementRepository.save(m);

        if (type == CashMovementType.CONTRIBUTION) {
            Reimbursement r = reimbursementRepository.save(Reimbursement.builder()
                    .tienda(cut.getTienda())
                    .person(saved.getPerson())
                    .amount(amount)
                    .reason(saved.getConcept())
                    .sourceCut(cut)
                    .status(ReimbursementStatus.PENDING)
                    .build());
            saved.setReimbursement(r);
            saved = movementRepository.save(saved);
        }
        return saved;
    }

    /**
     * Paga un reembolso pendiente con dinero de la caja de un corte abierto (lo usa
     * {@link ReimbursementService#pay} con origen "de la caja").
     */
    @Transactional
    public CashMovement payReimbursementFromCut(Long cutId, Long reimbursementId, String receiptPath, User actor) {
        CashCut cut = lockOpenCut(cutId, actor);
        return payReimbursement(cut, reimbursementId, receiptPath, actor);
    }

    // Siempre por el monto completo: monto, persona y concepto salen del propio reembolso,
    // nunca de lo que mande el cliente.
    private CashMovement payReimbursement(CashCut cut, Long reimbursementId, String receiptPath, User actor) {
        Reimbursement r = reimbursementRepository.findByIdForUpdate(reimbursementId)
                .filter(x -> x.getTienda().getId().equals(cut.getTienda().getId()))
                .orElseThrow(() -> new IllegalArgumentException("Reembolso no encontrado"));
        if (r.getStatus() != ReimbursementStatus.PENDING) {
            throw new IllegalStateException("Este reembolso ya se pagó");
        }
        ensureAvailable(calculator.expectedCash(cut), r.getAmount(), isBlind(cut, actor));

        CashMovement m = newMovement(cut.getTienda(), cut.getCashRegister(), CashMovementType.REIMBURSEMENT, r.getAmount(), actor);
        m.setCashCut(cut);
        m.setPerson(r.getPerson());
        String source = r.getSourceCut() != null ? " (corte #" + r.getSourceCut().getId() + ")" : "";
        m.setConcept(truncate("Reembolso a " + r.getPerson().getName() + source, 150));
        m.setReceiptPath(blankToNull(receiptPath));
        m.setReimbursement(r);
        CashMovement saved = movementRepository.save(m);

        r.setStatus(ReimbursementStatus.PAID);
        r.setPaidAt(LocalDateTime.now());
        r.setPaidRegisteredBy(actor);
        r.setPaymentSource(ReimbursementPaymentSource.CASH_CUT);
        r.setPaymentCut(cut);
        reimbursementRepository.save(r);
        return saved;
    }

    /**
     * Registra un retiro o una entrada de efectivo con la caja cerrada (entre turnos). Ajusta
     * la entrega pendiente de la caja; si el administrador cuenta lo que queda
     * ({@code countedRemaining}), la entrega pasa a ser lo que contó, verificada por él.
     *
     * @throws AccessDeniedException si el actor no es administrador/supervisor
     * @throws IllegalStateException si la caja está en uso, el tipo no aplica o el retiro excede lo que hay
     */
    @Transactional
    public CashMovement registerOnClosedRegister(Long registerId, CashMovementRequest req, User actor) {
        if (!tenantScope.isAdminLevel(actor)) {
            throw new AccessDeniedException("Solo un administrador o supervisor puede mover efectivo con la caja cerrada");
        }
        Tienda tienda = requireTienda(actor);
        CashRegister register = cashRegisterRepository.findByIdForUpdate(registerId)
                .filter(r -> r.getTienda().getId().equals(tienda.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Caja no encontrada"));
        if (cashCutRepository.findFirstByCashRegisterIdAndStatus(register.getId(), CashCutStatus.OPEN).isPresent()) {
            throw new IllegalStateException("La caja está en uso: registra el movimiento dentro de su corte");
        }
        CashMovementType type = req.getType();
        if (type != CashMovementType.WITHDRAWAL && type != CashMovementType.FLOAT) {
            throw new IllegalStateException("Con la caja cerrada solo se puede retirar o agregar efectivo");
        }

        BigDecimal amount = requireAmount(req.getAmount());
        BigDecimal current = register.getPendingAmount() != null ? register.getPendingAmount() : BigDecimal.ZERO;
        if (type.isOutflow()) ensureAvailable(current, amount, false);
        BigDecimal expectedRemaining = type.isOutflow() ? current.subtract(amount) : current.add(amount);

        CashMovement m = newMovement(tienda, register, type, amount, actor);
        applyDetails(m, req, tienda, actor);
        m.setExpectedRemaining(expectedRemaining);
        m.setCountedRemaining(req.getCountedRemaining());
        CashMovement saved = movementRepository.save(m);

        if (req.getCountedRemaining() != null) {
            register.setPendingAmount(req.getCountedRemaining());
            register.setPendingCountMethod(CashCountMethod.ADMIN_IN_PERSON);
            register.setPendingCountedBy(actor);
            register.setPendingCountedAt(LocalDateTime.now());
        } else {
            register.setPendingAmount(expectedRemaining);
        }
        cashRegisterRepository.save(register);
        return saved;
    }

    /** Sube la foto de un comprobante (ticket de gasto, captura de transferencia, evidencia de conteo). */
    public String uploadReceipt(MultipartFile file, User actor) {
        Tienda tienda = requireTienda(actor);
        return imageStorageService.guardar(file, "cash/" + tienda.getId(), "comprobante", "El comprobante");
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private CashCut lockOpenCut(Long cutId, User actor) {
        CashCut cut = cashCutRepository.findByIdForUpdate(cutId)
                .orElseThrow(() -> new IllegalArgumentException("Corte no encontrado: " + cutId));
        boolean own = cut.getUser().getId().equals(actor.getId());
        if (!own && !(tenantScope.isAdminLevel(actor) && inScope(cut.getTienda(), actor))) {
            throw new IllegalArgumentException("Corte no encontrado: " + cutId);
        }
        if (cut.getStatus() != CashCutStatus.OPEN) {
            throw new IllegalStateException("El corte ya está cerrado");
        }
        return cut;
    }

    private boolean inScope(Tienda tienda, User actor) {
        Long scope = tenantScope.scopeId(actor);
        if (scope == null) return tenantScope.isSuperAdmin(actor);
        return tienda != null && scope.equals(tienda.getId());
    }

    private Tienda requireTienda(User actor) {
        Tienda tienda = tenantScope.tiendaForWrite(actor);
        if (tienda == null) throw new IllegalStateException("Elige una tienda para continuar");
        return tienda;
    }

    private CashMovement newMovement(Tienda tienda, CashRegister register, CashMovementType type, BigDecimal amount, User actor) {
        CashMovement m = new CashMovement();
        m.setTienda(tienda);
        m.setCashRegister(register);
        m.setType(type);
        m.setAmount(amount);
        m.setRegisteredBy(actor);
        m.setOccurredAt(LocalDateTime.now());
        return m;
    }

    /** Llena concepto, persona, destino, referencia y comprobante, validando lo obligatorio de cada tipo. */
    private void applyDetails(CashMovement m, CashMovementRequest req, Tienda tienda, User actor) {
        String concept = blankToNull(req.getConcept());
        switch (m.getType()) {
            case EXPENSE -> {
                if (concept == null) throw new IllegalStateException("Escribe el concepto del gasto");
            }
            case WITHDRAWAL -> {
                if (req.getDestination() == null) throw new IllegalStateException("Indica el destino del dinero");
                m.setDestination(req.getDestination());
                m.setPerson(req.getPersonId() != null ? findPerson(req.getPersonId(), tienda, actor) : actor);
            }
            case CONTRIBUTION -> {
                if (req.getPersonId() == null) throw new IllegalStateException("Indica quién puso el dinero");
                if (concept == null) throw new IllegalStateException("Escribe el motivo del aporte");
                m.setPerson(findPerson(req.getPersonId(), tienda, actor));
            }
            case LOAN_OUT, LOAN_IN -> {
                // Préstamo entre cajas: cada caja registra su lado por separado (la que presta
                // una salida, la que recibe una entrada); aquí solo se anota cuál es la otra.
                if (req.getCounterpartRegisterId() == null) throw new IllegalStateException("Elige la otra caja");
                CashRegister other = cashRegisterRepository.findById(req.getCounterpartRegisterId())
                        .filter(r -> r.getTienda().getId().equals(tienda.getId()))
                        .orElseThrow(() -> new IllegalArgumentException("Caja no encontrada"));
                if (m.getCashRegister() != null && other.getId().equals(m.getCashRegister().getId())) {
                    throw new IllegalStateException("Elige una caja distinta a la tuya");
                }
                m.setCounterpartRegister(other);
                if (concept == null) {
                    concept = (m.getType() == CashMovementType.LOAN_OUT ? "Préstamo a " : "Préstamo de ") + other.getName();
                }
            }
            default -> { /* FLOAT: concepto opcional */ }
        }
        m.setConcept(concept);
        m.setReference(blankToNull(req.getReference()));
        m.setReceiptPath(blankToNull(req.getReceiptPath()));
    }

    // Persona de la misma tienda, o el propio actor (un SUPERVISOR/SUPER_ADMIN no tiene tienda propia).
    User findPerson(Long personId, Tienda tienda, User actor) {
        if (personId.equals(actor.getId())) return actor;
        User person = userRepository.findById(personId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        if (person.getTienda() == null || !person.getTienda().getId().equals(tienda.getId())) {
            throw new IllegalArgumentException("Usuario no encontrado");
        }
        return person;
    }

    private static BigDecimal requireAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) throw new IllegalStateException("Captura un monto mayor a cero");
        return amount;
    }

    // Con conteo ciego el mensaje no dice cuánto hay: revelaría el efectivo esperado.
    private static void ensureAvailable(BigDecimal available, BigDecimal amount, boolean blind) {
        if (amount.compareTo(available) > 0) {
            throw new IllegalStateException(blind
                    ? "No hay suficiente efectivo en caja para esta salida. Si alguien pagó con su propio dinero, registra primero su aporte."
                    : "Solo hay " + MONEY.format(available.max(BigDecimal.ZERO)) + " en caja. No se puede sacar más efectivo del que hay.");
        }
    }

    private boolean isBlind(CashCut cut, User actor) {
        return cut.getTienda() != null && Boolean.TRUE.equals(cut.getTienda().getBlindCashCount())
                && !tenantScope.isAdminLevel(actor);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
