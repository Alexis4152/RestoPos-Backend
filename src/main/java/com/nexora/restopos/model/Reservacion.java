package com.nexora.restopos.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Reservación de mesa hecha desde la página web de la tienda (o capturada por el personal
 * cuando el cliente llama). Llega como PENDIENTE y el personal la confirma asignándole mesa
 * (o la rechaza). Guarda las necesidades especiales del grupo — periqueras, mesa accesible
 * para silla de ruedas, carriola, ocasión — para que el restaurante prepare la mesa antes de
 * que lleguen. Ver ReservacionService.
 */
@Entity
@Table(name = "reservaciones",
        uniqueConstraints = @UniqueConstraint(columnNames = "token"),
        indexes = @Index(name = "idx_reservaciones_tienda_fecha", columnList = "tienda_id, fecha_hora"))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Reservacion {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Bloqueo optimista: dos personas del staff confirmando/rechazando la misma reservación.
    @Version
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private Long version;

    @JsonIgnoreProperties({"supervisor", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tienda_id", nullable = false)
    private Tienda tienda;

    // Cliente registrado que la hizo desde su cuenta (null = invitado o capturada por el personal).
    @JsonIgnoreProperties({"tienda"})
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(nullable = false, length = 30)
    private String telefono;

    @Column(length = 150)
    private String email;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    @Column(nullable = false)
    private Integer personas;

    // Zona que prefiere el cliente (terraza, salón...); null = sin preferencia.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "zona_id")
    private Zona zonaPreferida;

    // Mesa que asignó el personal al confirmar (null mientras no se asigne).
    @JsonIgnoreProperties({"ordenActiva", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "mesa_id")
    private Mesa mesa;

    // ── Necesidades especiales ──
    @Column(nullable = false, columnDefinition = "integer default 0")
    @Builder.Default
    private Integer periqueras = 0;

    // Alguien usa silla de ruedas o tiene movilidad reducida: mesa accesible, sin escalones.
    @Column(nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private Boolean accesible = false;

    // Llevan carriola (espacio junto a la mesa).
    @Column(nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private Boolean carriola = false;

    // Cumpleaños, aniversario, reunión de trabajo... (texto corto, opcional).
    @Column(length = 60)
    private String ocasion;

    // Otras indicaciones del cliente (alergias, ubicación, etc.).
    @Column(length = 500)
    private String notas;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    @Builder.Default
    private ReservacionEstado estado = ReservacionEstado.PENDIENTE;

    // Motivo del rechazo o de la cancelación (el cliente lo ve en su página).
    @Column(length = 300)
    private String motivo;

    // El cliente avisó que llegará unos minutos tarde (15/30/45/60); null = a tiempo.
    private Integer retrasoMinutos;

    // Última vez que el cliente cambió personas/necesidades, avisó retraso u ordenó (se le
    // destaca al personal).
    private LocalDateTime modificadaAt;

    // Pedido anticipado desde la página web: espera (sin ir a cocina) hasta que el personal
    // marca "Llegó"; ahí se abre en la mesa y entra a cocina. Ver OrdenService#preordenReservacion.
    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preorden_id")
    private Orden preorden;

    // WEB (página pública) o PERSONAL (capturada en el punto de venta, p. ej. por teléfono).
    @Column(nullable = false, length = 10)
    @Builder.Default
    private String origen = "WEB";

    // Código aleatorio con el que el cliente consulta/cancela su reservación sin cuenta.
    @Column(nullable = false, length = 64)
    private String token;

    // Quien la confirmó / rechazó / capturó.
    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atendida_por_id")
    private User atendidaPor;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
