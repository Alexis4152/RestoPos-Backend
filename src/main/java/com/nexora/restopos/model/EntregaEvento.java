package com.nexora.restopos.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Histórico de un envío a domicilio: cada paso del repartidor queda registrado con su hora
 * (TOMADO, SOLTADO, EN_CAMINO, ENTREGADO). Así se ve en el punto de venta quién lo tomó,
 * si se lo pasó a otro repartidor, cuándo salió y cuándo lo entregó. Ver DeliveryService.
 */
@Entity
@Table(name = "entrega_eventos", indexes = @Index(name = "idx_entrega_eventos_orden", columnList = "orden_id"))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class EntregaEvento {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "orden_id", nullable = false)
    private Long ordenId;

    // TOMADO, SOLTADO, EN_CAMINO, ENTREGADO
    @Column(nullable = false, length = 15)
    private String tipo;

    // Repartidor al que se refiere el paso (el que lo tomó / soltó / llevó).
    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "repartidor_id")
    private User repartidor;

    // Quien hizo el movimiento (normalmente el mismo repartidor; un admin puede soltar o entregar).
    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "actor_id")
    private User actor;

    // Dato extra (ej. cómo pagó el cliente al entregar).
    @Column(length = 100)
    private String detalle;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
