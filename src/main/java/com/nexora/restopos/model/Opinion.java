package com.nexora.restopos.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Opinión del cliente sobre una {@link Cuenta} pagada de una orden de MESA (consumo en el
 * restaurante): estrellas de la experiencia, estrellas de la atención del mesero, comentario y
 * 👍/👎 por platillo. Una sola por cuenta (si la mesa pagó separado, cada quien opina de lo
 * suyo). Se deja desde el QR del ticket (sin cuenta, ver Cuenta.opinionToken) o desde
 * "Mis pedidos" si la orden quedó ligada a un cliente registrado. Ver OpinionService.
 */
@Entity
@Table(name = "opiniones", uniqueConstraints = @UniqueConstraint(columnNames = "cuenta_id"))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Opinion {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnoreProperties({"supervisor", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tienda_id")
    private Tienda tienda;

    @JsonIgnoreProperties({"orden", "items", "pagos"})
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuenta_id", nullable = false)
    private Cuenta cuenta;

    @JsonIgnoreProperties({"items", "comensales", "mesa"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orden_id", nullable = false)
    private Orden orden;

    // Mesero que atendió (el de la orden) — para el promedio de atención por mesero.
    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "mesero_id")
    private User mesero;

    // Cliente registrado que opinó desde "Mis pedidos" (null = desde el QR, anónimo).
    @JsonIgnoreProperties({"tienda"})
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    // QR (ticket) o CUENTA ("Mis pedidos" del cliente registrado).
    @Column(nullable = false, length = 10)
    private String canal;

    @Column(nullable = false)
    private Integer estrellas;

    private Integer meseroEstrellas;

    @Column(columnDefinition = "TEXT")
    private String comentario;

    @JsonManagedReference
    @OneToMany(mappedBy = "opinion", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<OpinionPlatillo> platillos = new ArrayList<>();

    @CreationTimestamp
    private LocalDateTime createdAt;
}
