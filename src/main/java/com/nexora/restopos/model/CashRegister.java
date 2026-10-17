package com.nexora.restopos.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Caja: un cajón físico de efectivo dentro de una {@link Tienda}. Cada {@link CashCut}
 * (turno) se abre sobre una caja, y una caja solo puede tener un corte abierto a la vez.
 * <p>
 * Mientras la caja está cerrada (sin corte abierto), {@code pendingAmount} es el efectivo
 * que DEBE haber en ella: lo que dejó el último corte al cerrar, más/menos los movimientos
 * que un administrador haga con la caja cerrada ({@link CashMovement} sin corte). Quien abra
 * el siguiente corte recibe esa cantidad, la cuenta y da su de acuerdo. Los campos
 * {@code pending*} quedan en null mientras hay un corte abierto (o si la caja nunca se ha usado).
 */
@Entity
@Table(name = "cash_registers")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CashRegister {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tienda_id", nullable = false)
    private Tienda tienda;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    private LocalDateTime createdAt;

    /** Efectivo que debe haber en la caja cerrada; null si hay un corte abierto o nunca se ha usado. */
    @Column(precision = 12, scale = 2)
    private BigDecimal pendingAmount;

    /** Cómo se verificó por última vez {@code pendingAmount}. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private CashCountMethod pendingCountMethod;

    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "pending_counted_by_user_id")
    private User pendingCountedBy;

    private LocalDateTime pendingCountedAt;

    /**
     * {@code true} tras su "Corte de fin de día": no se puede abrir hasta que el job
     * programado la reabra (o hasta el día siguiente, si el job no corre). Ver CashDayCloseService.
     */
    @Column(nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private Boolean dayClosed = false;

    /** Último fin de día (manual o del job): los cortes cerrados después van en el siguiente reporte. */
    private LocalDateTime lastDayCloseAt;

    /** Último corte cerrado en esta caja (de él sale la entrega pendiente). */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_cut_id")
    private CashCut lastCut;
}
