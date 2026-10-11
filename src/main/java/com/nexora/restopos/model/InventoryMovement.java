package com.nexora.restopos.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Registro histórico e inmutable de un cambio en el stock de un {@link Product}.
 * <p>
 * Se crea automáticamente cada vez que el stock de un producto cambia (venta, cancelación
 * de venta, entrada/salida manual o ajuste de inventario), guardando una "fotografía" del
 * stock antes y después del movimiento para poder auditar el historial completo de un producto.
 */
@Entity
@Table(name = "inventory_movements")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class InventoryMovement {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Usuario que provocó el movimiento (quien hizo la venta, el ajuste manual, etc.). */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private MovementType type;

    /** Cantidad afectada por el movimiento (positiva; el signo/efecto lo determina {@code type}). */
    @Column(nullable = false)
    private Integer quantity;

    /** Stock del producto inmediatamente antes de aplicar este movimiento. */
    @Column(nullable = false)
    private Integer previousStock;

    /** Stock del producto inmediatamente después de aplicar este movimiento. */
    @Column(nullable = false)
    private Integer newStock;

    /** Motivo capturado por el usuario, principalmente para movimientos manuales/ajustes. */
    @Column(length = 255)
    private String reason;

    /** Referencia externa opcional (ej. folio de venta o de compra) asociada al movimiento. */
    @Column(length = 100)
    private String reference;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
