package com.nexora.restopos.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Comensal dentro de una {@link Orden} (ej. "Comensal 1"). No requiere datos personales: solo
 * un número de asiento y, opcionalmente, un nombre. Permite atribuir {@link OrdenDetalle} a
 * cada persona de la mesa para poder dividir la cuenta por comensal.
 */
@Entity
@Table(name = "comensales")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Comensal {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orden_id", nullable = false)
    private Orden orden;

    @Column(nullable = false)
    private Integer numero;

    @Column(length = 100)
    private String nombre;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
