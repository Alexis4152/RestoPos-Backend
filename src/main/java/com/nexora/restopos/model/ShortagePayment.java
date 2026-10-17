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
 * Un pago o abono a un {@link Shortage}. Si el efectivo entró a una caja, {@code cashMovement}
 * apunta a la entrada que se registró en ese corte.
 */
@Entity
@Table(name = "shortage_payments")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ShortagePayment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shortage_id", nullable = false)
    private Shortage shortage;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private ShortagePaymentMethod method;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cash_movement_id")
    private CashMovement cashMovement;

    @Column(length = 100)
    private String reference;

    @Column(length = 300)
    private String receiptPath;

    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "registered_by_user_id", nullable = false)
    private User registeredBy;

    @CreationTimestamp
    private LocalDateTime createdAt;

    /** Corte al que entró el efectivo (solo {@link ShortagePaymentMethod#CASH_REGISTER}). */
    @JsonProperty("cashCutId")
    public Long getCashCutId() {
        return cashMovement != null ? cashMovement.getCashCutId() : null;
    }
}
