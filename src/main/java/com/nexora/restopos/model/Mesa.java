package com.nexora.restopos.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Mesa física del restaurante, propiedad de una {@link Tienda}. Su {@link #ordenActiva} es la
 * {@link Orden} abierta actualmente sobre ella (null si está libre).
 */
@Entity
@Table(name = "mesas")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@EntityListeners(com.nexora.restopos.service.CambiosListener.class)
public class Mesa {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Bloqueo optimista: dos meseros abriendo la misma mesa al mismo tiempo — el segundo
    // recibe un 409 (y no se crea una segunda orden) en vez de quedar dos órdenes en la mesa.
    @Version
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private Long version;

    @Column(nullable = false, length = 20)
    private String numero;

    private Integer capacidad;

    // nullable a nivel de base de datos (mesas creadas antes de que existiera este campo),
    // pero MesaService.create la exige — ver TenantDataInitializer#backfillZonas para el
    // backfill de las mesas que ya existían.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "zona_id")
    private Zona zona;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private MesaEstado estado = MesaEstado.DISPONIBLE;

    // Se puede asignar a reservaciones. Solo cuenta si la tienda eligió "solo algunas mesas"
    // (TiendaInfo.reservaTodasMesas = false); con "todas", cualquier mesa activa se reserva.
    @Column(nullable = false, columnDefinition = "boolean default true")
    @Builder.Default
    private Boolean reservable = true;

    // trimmed a propósito ("mesa"): Orden.mesa apunta de vuelta a esta misma fila, y sin este
    // corte Jackson entraría en ciclo infinito Mesa->ordenActiva->mesa->ordenActiva->...
    @JsonIgnoreProperties({"mesa", "items", "comensales"})
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "orden_activa_id")
    private Orden ordenActiva;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tienda_id")
    private Tienda tienda;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_user_id")
    private User updatedBy;

    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deleted_by_user_id")
    private User deletedBy;

    private LocalDateTime deletedAt;

    /**
     * Si esta mesa está unida a otra (comparte la orden de la mesa principal), el número de
     * la principal; null si es la principal o está libre. Solo para el JSON del plano.
     */
    @com.fasterxml.jackson.annotation.JsonProperty("unidaA")
    public String getUnidaA() {
        if (ordenActiva == null || ordenActiva.getMesa() == null || ordenActiva.getMesa().getId().equals(id)) return null;
        return ordenActiva.getMesa().getNumero();
    }

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
