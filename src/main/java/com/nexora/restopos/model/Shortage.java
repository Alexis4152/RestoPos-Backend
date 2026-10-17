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
 * Faltante de efectivo a cargo de una persona: se genera cuando un administrador resuelve
 * una diferencia de entrega de caja como "faltante confirmado" y elige al responsable. La
 * persona lo va cubriendo con pagos/abonos ({@link ShortagePayment}) hasta saldarlo.
 */
@Entity
@Table(name = "shortages")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Shortage {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tienda_id", nullable = false)
    private Tienda tienda;

    /** Responsable del faltante: quien lo debe cubrir. */
    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "person_user_id", nullable = false)
    private User person;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 12, scale = 2, columnDefinition = "numeric(12,2) default 0")
    @Builder.Default
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private ShortageStatus status = ShortageStatus.PENDING;

    /** Corte que recibió la caja con la diferencia (la entrega de la que salió el faltante). */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_cut_id")
    private CashCut sourceCut;

    /** Si salió de una diferencia al recibir la caja o del arqueo al cerrar. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, columnDefinition = "varchar(10) default 'HANDOVER'")
    @Builder.Default
    private ShortageOrigin origin = ShortageOrigin.HANDOVER;

    /** Nota del administrador al resolver la diferencia. */
    @Column(length = 200)
    private String reason;

    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdBy;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @JsonProperty("remaining")
    public BigDecimal getRemaining() {
        return amount.subtract(paidAmount != null ? paidAmount : BigDecimal.ZERO);
    }

    @JsonProperty("sourceCutId")
    public Long getSourceCutId() {
        return sourceCut != null ? sourceCut.getId() : null;
    }

    @JsonProperty("registerName")
    public String getRegisterName() {
        return sourceCut != null && sourceCut.getCashRegister() != null ? sourceCut.getCashRegister().getName() : null;
    }
}
