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
 * Movimiento de efectivo de una caja que no es una venta: gastos, retiros, pagos de
 * reembolso, aportes y entradas de cambio (ver {@link CashMovementType}).
 * <p>
 * Normalmente pertenece a un {@link CashCut} abierto ({@code cashCut}). Si un administrador
 * lo registra con la caja cerrada (entre turnos), {@code cashCut} queda en null y el
 * movimiento ajusta la entrega pendiente de la caja; cuando alguien abre el siguiente corte,
 * {@code receivedByCut} apunta a ese corte, que es el que vio el movimiento en el desglose
 * de lo que recibió.
 */
@Entity
@Table(name = "cash_movements")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CashMovement {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tienda_id", nullable = false)
    private Tienda tienda;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cash_register_id")
    private CashRegister cashRegister;

    /** Corte en el que se registró; null si se registró con la caja cerrada. */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cash_cut_id")
    private CashCut cashCut;

    /** Para movimientos con la caja cerrada: el corte que recibió la caja después de ellos. */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "received_by_cut_id")
    private CashCut receivedByCut;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CashMovementType type;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(length = 150)
    private String concept;

    /** Quién puso el dinero (aporte), quién retiró (retiro) o a quién se le pagó (reembolso). */
    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "person_user_id")
    private User person;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private WithdrawalDestination destination;

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

    /** Cuándo ocurrió de verdad; difiere de {@code createdAt} solo si se registró después. */
    private LocalDateTime occurredAt;

    /** {@code true} si se registró después de ocurrido (al resolver una diferencia de entrega). */
    @Column(nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private Boolean lateRegistered = false;

    /** Con la caja cerrada: lo que debía quedar después del movimiento, y lo que el admin contó (si contó). */
    @Column(precision = 12, scale = 2)
    private BigDecimal expectedRemaining;

    @Column(precision = 12, scale = 2)
    private BigDecimal countedRemaining;

    /** Préstamo entre cajas: la otra caja (a la que se prestó o de la que se recibió). */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "counterpart_register_id")
    private CashRegister counterpartRegister;

    @JsonProperty("counterpartRegisterName")
    public String getCounterpartRegisterName() {
        return counterpartRegister != null ? counterpartRegister.getName() : null;
    }

    /** Reembolso que creó (aporte) o que pagó (pago de reembolso). */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reimbursement_id")
    private Reimbursement reimbursement;

    @JsonProperty("cashCutId")
    public Long getCashCutId() {
        return cashCut != null ? cashCut.getId() : null;
    }

    @JsonProperty("reimbursementId")
    public Long getReimbursementId() {
        return reimbursement != null ? reimbursement.getId() : null;
    }
}
