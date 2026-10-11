package com.nexora.restopos.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Corte de caja: representa la sesión de trabajo de un cajero/vendedor dentro de una
 * {@link Tienda}, desde que la abre (con un fondo inicial en {@code openingAmount}) hasta
 * que se cierra.
 * <p>
 * Cada usuario abre y opera su propio corte, por lo que en una misma tienda pueden existir
 * varios cortes {@link CashCutStatus#OPEN} simultáneamente (uno por cajero). Todas las
 * {@link Sale} de un cajero se asocian a su corte abierto, y este va acumulando los totales
 * (ventas por forma de pago, transacciones, cancelaciones, etc.) que se consolidan al cerrarlo.
 * <p>
 * El cierre puede ser manual (el propio cajero o un ADMIN) o automático, ejecutado por
 * {@code CashCutAutoCloseJob} según el horario configurado en {@link CashCutSchedule}.
 */
@Entity
@Table(name = "cash_cuts")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CashCut {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // quien abrió el corte (siempre el propio cajero/vendedor)
    // JsonIgnoreProperties: evita que Jackson expanda role/tienda (y sus propios
    // createdBy/updatedBy/deletedBy) de este User, lo que crearía un ciclo infinito
    // User→role→tienda→updatedBy(User)→role→... al serializar la respuesta.
    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // quien lo cerró — null mientras sigue abierto, y también null si lo cerró el job
    // automático (CashCutAutoCloseJob) en vez de una persona; el frontend muestra "Sistema".
    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "closed_by_user_id")
    private User closedBy;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tienda_id")
    private Tienda tienda;

    /** Fondo inicial (efectivo) con el que el cajero abre el corte. */
    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal openingAmount = BigDecimal.ZERO;

    /** Monto final contado al cerrar el corte; null mientras sigue abierto. */
    @Column(precision = 12, scale = 2)
    private BigDecimal closingAmount;

    /** Total de gastos/salidas de efectivo registrados durante el corte. */
    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal expenses = BigDecimal.ZERO;

    /** Suma total de las ventas completadas del corte, sin importar la forma de pago. */
    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalSales = BigDecimal.ZERO;

    /** Subtotal de ventas pagadas en efectivo ({@link PaymentMethod#CASH}). */
    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal cashSales = BigDecimal.ZERO;

    /** Subtotal de ventas pagadas con tarjeta ({@link PaymentMethod#CARD}). */
    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal cardSales = BigDecimal.ZERO;

    /** Subtotal de ventas pagadas por transferencia ({@link PaymentMethod#TRANSFER}). */
    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal transferSales = BigDecimal.ZERO;

    /** Número total de ventas completadas dentro del corte. */
    @Column(nullable = false)
    @Builder.Default
    private Integer totalTransactions = 0;

    /** Número de ventas de este corte que fueron canceladas ({@link SaleStatus#CANCELLED}). */
    // columnDefinition incluye un DEFAULT para que el ALTER TABLE funcione contra filas ya existentes
    @Column(nullable = false, columnDefinition = "integer default 0")
    @Builder.Default
    private Integer cancelledCount = 0;

    /** Monto acumulado de las ventas canceladas del corte (no forma parte de {@code totalSales}). */
    @Column(nullable = false, precision = 12, scale = 2, columnDefinition = "numeric(12,2) default 0")
    @Builder.Default
    private BigDecimal cancelledTotal = BigDecimal.ZERO;

    /** Total de entradas manuales de efectivo ({@link MovimientoCaja}) registradas durante el corte. */
    @Column(nullable = false, precision = 12, scale = 2, columnDefinition = "numeric(12,2) default 0")
    @Builder.Default
    private BigDecimal totalEntradas = BigDecimal.ZERO;

    /** Total de salidas manuales de efectivo ({@link MovimientoCaja}) registradas durante el corte. */
    @Column(nullable = false, precision = 12, scale = 2, columnDefinition = "numeric(12,2) default 0")
    @Builder.Default
    private BigDecimal totalSalidas = BigDecimal.ZERO;

    /** Estado actual del corte: abierto o cerrado. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private CashCutStatus status = CashCutStatus.OPEN;

    /** Notas libres capturadas por el cajero/ADMIN al abrir o cerrar el corte. */
    @Column(columnDefinition = "TEXT")
    private String notes;

    /** Fecha/hora en que se abrió el corte. */
    @CreationTimestamp
    private LocalDateTime openedAt;

    /** Fecha/hora en que se cerró el corte; null mientras sigue {@link CashCutStatus#OPEN}. */
    private LocalDateTime closedAt;
}
