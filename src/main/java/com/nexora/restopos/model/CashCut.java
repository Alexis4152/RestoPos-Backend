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
 * Corte de caja: el turno de un cajero/vendedor sobre una {@link CashRegister} (caja) de
 * una {@link Tienda}, desde que la recibe (con el fondo inicial que contó, en
 * {@code openingAmount}) hasta que la cierra y la entrega.
 * <p>
 * Cada usuario tiene a lo más un corte abierto, y cada caja también: en una tienda con
 * varias cajas puede haber varios cortes {@link CashCutStatus#OPEN} a la vez (uno por caja).
 * Todas las {@link Sale} de un cajero se asocian a su corte abierto; los demás movimientos
 * de efectivo (gastos, retiros, aportes...) son {@link CashMovement}.
 * <p>
 * Al abrir, el cajero recibe lo que dejó el corte anterior de esa caja (más/menos lo que un
 * administrador movió con la caja cerrada), lo cuenta y da su de acuerdo: si no coincide,
 * queda una diferencia de entrega ({@code handover*}). Al cerrar se hace el arqueo
 * ({@code countedAmount} contra el efectivo esperado) y lo contado queda en la caja para el
 * siguiente turno ({@code handoverAmount}).
 * <p>
 * El cierre puede ser manual (el propio cajero), forzado por un administrador, o automático,
 * ejecutado por {@code CashCutAutoCloseJob} según el horario configurado en {@link CashCutSchedule}.
 * Los cortes anteriores a las cajas tienen {@code cashRegister} en null.
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

    /** Estado actual del corte: abierto o cerrado. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private CashCutStatus status = CashCutStatus.OPEN;

    // ── Caja y recepción ───────────────────────────────────────────────────────────
    /** Caja sobre la que se abrió el corte; null en cortes anteriores a las cajas. */
    @JsonIgnoreProperties({"tienda", "pendingCountedBy", "hibernateLazyInitializer", "handler"})
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cash_register_id")
    private CashRegister cashRegister;

    /** Corte anterior de la misma caja, del que se recibió el efectivo. */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_cut_id")
    private CashCut previousCut;

    /** Lo que debía recibir el cajero al abrir; null si no hubo entrega previa. */
    @Column(precision = 12, scale = 2)
    private BigDecimal expectedOpeningAmount;

    /** Cómo se había verificado el efectivo que recibió (snapshot de la caja al abrir). */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private CashCountMethod receivedCountMethod;

    /** Nota del cajero al recibir (obligatoria si contó otra cantidad). */
    @Column(columnDefinition = "TEXT")
    private String receiveNote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12, columnDefinition = "varchar(12) default 'NONE'")
    @Builder.Default
    private HandoverStatus handoverStatus = HandoverStatus.NONE;

    /** Contado al recibir menos lo que debía recibir (negativo = faltante). */
    @Column(precision = 12, scale = 2)
    private BigDecimal handoverDifference;

    @Enumerated(EnumType.STRING)
    @Column(length = 25)
    private HandoverResolution handoverResolution;

    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "handover_resolved_by_user_id")
    private User handoverResolvedBy;

    private LocalDateTime handoverResolvedAt;

    @Column(columnDefinition = "TEXT")
    private String handoverResolutionNote;

    // ── Resolución de la diferencia del arqueo de cierre (contado vs. esperado) ─────
    /** NONE si nadie contó al cerrar; OK si cuadró; DIFFERENCE pendiente; RESOLVED ya resuelta. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12, columnDefinition = "varchar(12) default 'NONE'")
    @Builder.Default
    private HandoverStatus arqueoStatus = HandoverStatus.NONE;

    @Enumerated(EnumType.STRING)
    @Column(length = 15)
    private ArqueoResolution arqueoResolution;

    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "arqueo_resolved_by_user_id")
    private User arqueoResolvedBy;

    private LocalDateTime arqueoResolvedAt;

    @Column(columnDefinition = "TEXT")
    private String arqueoResolutionNote;

    // ── Movimientos y arqueo (se consolidan al cerrar) ───────────────────────────
    /** Suma de entradas (aportes + cambio) del corte. */
    @Column(precision = 12, scale = 2)
    private BigDecimal entriesTotal;

    /** Suma de retiros del corte (no son gasto: no bajan la utilidad). */
    @Column(precision = 12, scale = 2)
    private BigDecimal withdrawalsTotal;

    /** Suma de reembolsos pagados desde la caja del corte. */
    @Column(precision = 12, scale = 2)
    private BigDecimal reimbursementsTotal;

    /** Suma de préstamos a otras cajas (no son gasto: no bajan la utilidad). */
    @Column(precision = 12, scale = 2)
    private BigDecimal loansOutTotal;

    /** Efectivo contado al cerrar; null si nadie lo contó. */
    @Column(precision = 12, scale = 2)
    private BigDecimal countedAmount;

    /** Contado menos esperado ({@code closingAmount}); null si nadie contó. */
    @Column(precision = 12, scale = 2)
    private BigDecimal cashDifference;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private CashCountMethod countMethod;

    /** Quién contó (o reportó, a distancia) el efectivo al cerrar. */
    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "counted_by_user_id")
    private User countedBy;

    /** Foto/captura del conteo reportado a distancia (cierre forzado). */
    @Column(length = 300)
    private String countEvidencePath;

    /** Lo que quedó en la caja para el siguiente turno (lo contado, o el esperado si nadie contó). */
    @Column(precision = 12, scale = 2)
    private BigDecimal handoverAmount;

    /**
     * Diferencia del arqueo tomando en cuenta la recepción de la caja. El esperado al cerrar
     * parte del fondo que se contó al recibir; si la diferencia al recibir se resolvió como
     * error de conteo cuando el corte ya estaba cerrado (su fondo no se pudo corregir), ese
     * fondo — y con él el esperado — quedó desfasado justo por esa diferencia: se descuenta
     * aquí. Ej.: debía recibir $100, contó $1,000 (+$900, error de conteo); al cerrar esperaba
     * $1,000 y contó $800 (−$200) → la diferencia real del turno es +$700.
     */
    @JsonProperty("effectiveCashDifference")
    public BigDecimal getEffectiveCashDifference() {
        if (cashDifference == null) return null;
        boolean receiveCountError = handoverResolution == HandoverResolution.COUNT_ERROR
                && handoverDifference != null && expectedOpeningAmount != null && openingAmount != null
                && openingAmount.compareTo(expectedOpeningAmount) != 0;
        return receiveCountError ? cashDifference.add(handoverDifference) : cashDifference;
    }

    @JsonProperty("previousCutId")
    public Long getPreviousCutId() {
        return previousCut != null ? previousCut.getId() : null;
    }

    @JsonProperty("previousUserName")
    public String getPreviousUserName() {
        return previousCut != null && previousCut.getUser() != null ? previousCut.getUser().getName() : null;
    }

    /** Notas libres capturadas por el cajero/ADMIN al abrir o cerrar el corte. */
    @Column(columnDefinition = "TEXT")
    private String notes;

    /** Fecha/hora en que se abrió el corte. */
    @CreationTimestamp
    private LocalDateTime openedAt;

    /** Fecha/hora en que se cerró el corte; null mientras sigue {@link CashCutStatus#OPEN}. */
    private LocalDateTime closedAt;
}
