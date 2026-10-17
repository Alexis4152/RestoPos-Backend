package com.nexora.restopos.service;

import com.nexora.restopos.dto.ShortagePaymentRequest;
import com.nexora.restopos.model.CashCut;
import com.nexora.restopos.model.CashCutStatus;
import com.nexora.restopos.model.CashMovement;
import com.nexora.restopos.model.CashMovementType;
import com.nexora.restopos.model.Shortage;
import com.nexora.restopos.model.ShortagePayment;
import com.nexora.restopos.model.ShortagePaymentMethod;
import com.nexora.restopos.model.ShortageStatus;
import com.nexora.restopos.model.User;
import com.nexora.restopos.repository.CashCutRepository;
import com.nexora.restopos.repository.CashMovementRepository;
import com.nexora.restopos.repository.ShortagePaymentRepository;
import com.nexora.restopos.repository.ShortageRepository;
import com.nexora.restopos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Faltantes ({@link Shortage}): dinero que una persona debe cubrir porque faltó en una
 * entrega de caja. Se crean al resolver una diferencia como faltante confirmado (ver
 * {@link CashRegisterService#resolveHandover}) y se saldan con pagos/abonos: el efectivo
 * puede entrar a la caja de un corte abierto (queda una entrada {@link
 * CashMovementType#SHORTAGE_PAYMENT} en ese corte) o cubrirse por fuera (transferencia,
 * efectivo al administrador o descuento de nómina). Todo es solo para administradores.
 */
@Service
@RequiredArgsConstructor
public class ShortageService {

    private static final NumberFormat MONEY = NumberFormat.getCurrencyInstance(new Locale("es", "MX"));

    private final ShortageRepository shortageRepository;
    private final ShortagePaymentRepository paymentRepository;
    private final CashCutRepository cashCutRepository;
    private final CashMovementRepository movementRepository;
    private final TenantScope tenantScope;

    /** Faltantes de la tienda del actor: abiertos (pendientes o con abonos) o pagados. */
    public Page<Shortage> search(boolean paid, Pageable pageable, User actor) {
        requireAdmin(actor);
        List<ShortageStatus> statuses = paid ? List.of(ShortageStatus.PAID) : List.of(ShortageStatus.PENDING, ShortageStatus.PARTIAL);
        return shortageRepository.search(tenantScope.scopeId(actor), statuses, pageable);
    }

    /** Total que falta por cubrir en la tienda del actor. */
    public BigDecimal pendingTotal(User actor) {
        requireAdmin(actor);
        return shortageRepository.sumPending(tenantScope.scopeId(actor));
    }

    /** Pagos/abonos de un faltante, en orden cronológico. */
    public List<ShortagePayment> payments(Long id, User actor) {
        requireAdmin(actor);
        findInScope(id, actor);
        return paymentRepository.findByShortage_IdOrderByCreatedAtAsc(id);
    }

    /**
     * Registra un pago total o un abono. No puede exceder lo que resta; al llegar a cero el
     * faltante queda pagado. Si el efectivo entra a una caja, se registra como entrada en el
     * corte abierto elegido (sube su efectivo esperado).
     *
     * @throws IllegalStateException si el faltante ya está pagado, el monto excede lo que
     *         resta o el corte elegido no está abierto
     */
    @Transactional
    public Shortage pay(Long id, ShortagePaymentRequest req, User actor) {
        requireAdmin(actor);
        findInScope(id, actor);
        Shortage s = shortageRepository.findByIdForUpdate(id).orElseThrow();
        if (s.getStatus() == ShortageStatus.PAID) throw new IllegalStateException("Este faltante ya está pagado");
        BigDecimal remaining = s.getRemaining();
        if (req.getAmount().compareTo(remaining) > 0) {
            throw new IllegalStateException("El monto excede lo que resta del faltante (" + MONEY.format(remaining) + ")");
        }

        ShortagePayment payment = new ShortagePayment();
        payment.setShortage(s);
        payment.setAmount(req.getAmount());
        payment.setMethod(req.getMethod());
        payment.setReference(blankToNull(req.getReference()));
        payment.setReceiptPath(blankToNull(req.getReceiptPath()));
        payment.setRegisteredBy(actor);

        if (req.getMethod() == ShortagePaymentMethod.CASH_REGISTER) {
            if (req.getCashCutId() == null) throw new IllegalStateException("Elige la caja a la que entra el dinero");
            CashCut cut = cashCutRepository.findByIdForUpdate(req.getCashCutId())
                    .filter(c -> c.getTienda() != null && c.getTienda().getId().equals(s.getTienda().getId()))
                    .orElseThrow(() -> new IllegalArgumentException("Corte no encontrado"));
            if (cut.getStatus() != CashCutStatus.OPEN) throw new IllegalStateException("El corte elegido ya está cerrado");
            CashMovement m = new CashMovement();
            m.setTienda(cut.getTienda());
            m.setCashRegister(cut.getCashRegister());
            m.setCashCut(cut);
            m.setType(CashMovementType.SHORTAGE_PAYMENT);
            m.setAmount(req.getAmount());
            m.setPerson(s.getPerson());
            String origin = s.getSourceCutId() != null ? " (faltante del corte #" + s.getSourceCutId() + ")" : "";
            m.setConcept(truncate("Pago de faltante de " + s.getPerson().getName() + origin, 150));
            m.setReference(payment.getReference());
            m.setReceiptPath(payment.getReceiptPath());
            m.setRegisteredBy(actor);
            m.setOccurredAt(LocalDateTime.now());
            payment.setCashMovement(movementRepository.save(m));
        }
        paymentRepository.save(payment);

        s.setPaidAmount(s.getPaidAmount().add(req.getAmount()));
        s.setStatus(s.getRemaining().signum() == 0 ? ShortageStatus.PAID : ShortageStatus.PARTIAL);
        return shortageRepository.save(s);
    }

    private Shortage findInScope(Long id, User actor) {
        Long scope = tenantScope.scopeId(actor);
        return shortageRepository.findById(id)
                .filter(s -> scope == null ? tenantScope.isSuperAdmin(actor) : scope.equals(s.getTienda().getId()))
                .orElseThrow(() -> new IllegalArgumentException("Faltante no encontrado"));
    }

    private void requireAdmin(User actor) {
        if (!tenantScope.isAdminLevel(actor)) {
            throw new AccessDeniedException("Solo un administrador o supervisor puede ver o cobrar faltantes");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
