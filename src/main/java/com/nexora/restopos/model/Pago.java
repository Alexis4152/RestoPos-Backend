package com.nexora.restopos.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Pago individual aplicado a una {@link Cuenta}. Un pago mixto (ej. parte efectivo, parte
 * tarjeta) se registra como varios {@link Pago} distintos sobre la misma cuenta, cada uno con
 * su propio {@link PaymentMethod}. Queda atado al {@link CashCut} abierto de quien lo cobra,
 * para poder consolidarlo en el corte de caja.
 */
@Entity
@Table(name = "pagos")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Pago {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuenta_id", nullable = false)
    private Cuenta cuenta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod metodo;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    // solo relevante para CASH: lo que el cliente entregó, para calcular el cambio en el ticket
    @Column(precision = 12, scale = 2)
    private BigDecimal recibido;

    @Column(length = 100)
    private String referencia;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cash_cut_id", nullable = false)
    private CashCut cashCut;

    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "usuario_id", nullable = false)
    private User usuario;

    @Column(nullable = false)
    @Builder.Default
    private Boolean cancelado = false;

    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by_user_id")
    private User cancelledBy;

    private LocalDateTime cancelledAt;

    @Column(length = 255)
    private String motivoCancelacion;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
