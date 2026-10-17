package com.nexora.restopos.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Dinero que la tienda le debe a una persona que puso efectivo de su bolsa en una caja
 * (un {@link CashMovement} de tipo {@link CashMovementType#CONTRIBUTION}). Se paga completo,
 * desde la caja de un corte abierto (queda una salida {@link CashMovementType#REIMBURSEMENT})
 * o por fuera de la caja (solo queda la constancia de quién, cuándo y cómo).
 */
@Entity
@Table(name = "reimbursements")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Reimbursement {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tienda_id", nullable = false)
    private Tienda tienda;

    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "person_user_id", nullable = false)
    private User person;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(length = 150)
    private String reason;

    /** Corte en el que se hizo el aporte. */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_cut_id")
    private CashCut sourceCut;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private ReimbursementStatus status = ReimbursementStatus.PENDING;

    @CreationTimestamp
    private LocalDateTime createdAt;

    // ── Pago ──────────────────────────────────────────────────────────────────────
    private LocalDateTime paidAt;

    /** Quién marcó el pago en el sistema. */
    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "paid_registered_by_user_id")
    private User paidRegisteredBy;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private ReimbursementPaymentSource paymentSource;

    /** Corte de cuya caja salió el pago (solo {@link ReimbursementPaymentSource#CASH_CUT}). */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_cut_id")
    private CashCut paymentCut;

    @Enumerated(EnumType.STRING)
    @Column(length = 15)
    private ExternalPaymentMethod externalMethod;

    /** Quién pagó de su dinero (solo {@link ReimbursementPaymentSource#EXTERNAL}). */
    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "external_paid_by_user_id")
    private User externalPaidBy;

    @Column(length = 100)
    private String reference;

    @Column(length = 300)
    private String receiptPath;

    @JsonProperty("sourceCutId")
    public Long getSourceCutId() {
        return sourceCut != null ? sourceCut.getId() : null;
    }

    @JsonProperty("paymentCutId")
    public Long getPaymentCutId() {
        return paymentCut != null ? paymentCut.getId() : null;
    }
}
