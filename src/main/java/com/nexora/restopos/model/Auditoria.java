package com.nexora.restopos.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Bitácora de operaciones sensibles del sistema (cancelaciones, apertura/corte de caja,
 * movimientos de efectivo, cancelación de pagos, etc.). {@code valorAnterior}/{@code valorNuevo}
 * guardan una representación JSON simple del estado relevante, no la entidad completa.
 */
@Entity
@Table(name = "auditoria")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Auditoria {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "usuario_id")
    private User usuario;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tienda_id")
    private Tienda tienda;

    @Column(nullable = false, length = 60)
    private String accion;

    @Column(nullable = false, length = 60)
    private String entidad;

    private Long entidadId;

    @Column(columnDefinition = "TEXT")
    private String valorAnterior;

    @Column(columnDefinition = "TEXT")
    private String valorNuevo;

    @Column(columnDefinition = "TEXT")
    private String motivo;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
