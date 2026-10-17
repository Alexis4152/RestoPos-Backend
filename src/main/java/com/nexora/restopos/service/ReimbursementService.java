package com.nexora.restopos.service;

import com.nexora.restopos.dto.ReimbursementPayRequest;
import com.nexora.restopos.model.Reimbursement;
import com.nexora.restopos.model.ReimbursementPaymentSource;
import com.nexora.restopos.model.ReimbursementStatus;
import com.nexora.restopos.model.Tienda;
import com.nexora.restopos.model.User;
import com.nexora.restopos.repository.ReimbursementRepository;
import com.nexora.restopos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Consulta y pago de {@link Reimbursement}s: el dinero que la tienda le debe a quien puso
 * efectivo de su bolsa en una caja. Pagar nunca es solo "marcar como pagado": se indica de
 * dónde salió el dinero — de la caja de un corte abierto (queda una salida en ese corte,
 * ver {@link CashMovementService#payReimbursementFromCut}) o por fuera de la caja (queda la
 * constancia de quién lo pagó, cuándo y cómo).
 */
@Service
@RequiredArgsConstructor
public class ReimbursementService {

    private final ReimbursementRepository reimbursementRepository;
    private final CashMovementService movementService;
    private final TenantScope tenantScope;

    /** Reembolsos de la tienda del actor, opcionalmente filtrados por estado. */
    public Page<Reimbursement> search(ReimbursementStatus status, Pageable pageable, User actor) {
        return reimbursementRepository.search(tenantScope.scopeId(actor), status, pageable);
    }

    /** Total pendiente de pagar en la tienda del actor. */
    public BigDecimal pendingTotal(User actor) {
        return reimbursementRepository.sumPending(tenantScope.scopeId(actor));
    }

    /**
     * Paga un reembolso pendiente, completo.
     *
     * @throws AccessDeniedException si el actor no es administrador/supervisor
     * @throws IllegalStateException si ya estaba pagado o faltan datos del pago
     */
    @Transactional
    public Reimbursement pay(Long id, ReimbursementPayRequest req, User actor) {
        if (!tenantScope.isAdminLevel(actor)) {
            throw new AccessDeniedException("Solo un administrador o supervisor puede pagar reembolsos desde aquí");
        }
        Reimbursement r = findInScope(id, actor);

        if (req.getSource() == ReimbursementPaymentSource.CASH_CUT) {
            if (req.getCashCutId() == null) throw new IllegalStateException("Elige la caja de la que sale el dinero");
            movementService.payReimbursementFromCut(req.getCashCutId(), r.getId(), req.getReceiptPath(), actor);
            return findInScope(id, actor);
        }

        r = reimbursementRepository.findByIdForUpdate(id).orElseThrow();
        if (r.getStatus() != ReimbursementStatus.PENDING) throw new IllegalStateException("Este reembolso ya se pagó");
        if (req.getMethod() == null) throw new IllegalStateException("Indica el método de pago");
        Tienda tienda = r.getTienda();
        User paidBy = req.getPaidById() != null
                ? movementService.findPerson(req.getPaidById(), tienda, actor)
                : actor;
        r.setStatus(ReimbursementStatus.PAID);
        r.setPaidAt(LocalDateTime.now());
        r.setPaidRegisteredBy(actor);
        r.setPaymentSource(ReimbursementPaymentSource.EXTERNAL);
        r.setExternalMethod(req.getMethod());
        r.setExternalPaidBy(paidBy);
        r.setReference(blankToNull(req.getReference()));
        r.setReceiptPath(blankToNull(req.getReceiptPath()));
        return reimbursementRepository.save(r);
    }

    private Reimbursement findInScope(Long id, User actor) {
        Long scope = tenantScope.scopeId(actor);
        return reimbursementRepository.findById(id)
                .filter(r -> scope == null ? tenantScope.isSuperAdmin(actor) : scope.equals(r.getTienda().getId()))
                .orElseThrow(() -> new IllegalArgumentException("Reembolso no encontrado"));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
