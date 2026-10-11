package com.nexora.restopos.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Una opción de modificador elegida sobre una línea de orden ({@link OrdenDetalle}) — ej. "Término:
 * Bien cocido" o "Extra: Tocino +$20". Guarda una COPIA de {@code grupoNombre}/{@code nombre}/
 * {@code precioExtra} al momento de pedir, igual que {@link OrdenDetalle#getProductoNombre()}: si
 * el grupo o la opción se editan o desactivan después, el ticket y la comanda ya impresos no
 * cambian. {@code precioExtra} ya está incluido en {@link OrdenDetalle#getSubtotal()} de su línea;
 * aquí solo se guarda para desglosarlo al imprimir.
 */
@Entity
@Table(name = "orden_detalle_modificadores")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class OrdenDetalleModificador {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orden_detalle_id", nullable = false)
    private OrdenDetalle ordenDetalle;

    @Column(nullable = false, length = 100)
    private String grupoNombre;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal precioExtra = BigDecimal.ZERO;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
