package com.nexora.restopos.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Movimiento manual de efectivo dentro de un {@link CashCut} (entrada, salida o ajuste), aparte
 * de las ventas normales (que ya quedan registradas una por una como {@link Pago}).
 */
@Entity
@Table(name = "movimientos_caja")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class MovimientoCaja {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cash_cut_id", nullable = false)
    private CashCut cashCut;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CashMovementType tipo;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Column(nullable = false, length = 255)
    private String motivo;

    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "usuario_id", nullable = false)
    private User usuario;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
