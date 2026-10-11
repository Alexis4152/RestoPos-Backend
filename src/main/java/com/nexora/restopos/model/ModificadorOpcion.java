package com.nexora.restopos.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Una opción dentro de un {@link ModificadorGrupo} (ej. "Bien cocido", "Extra queso +$15").
 * {@code precioExtra} se suma al precio del producto cuando se elige — puede ser $0 (ej. "sin
 * cebolla"). Nunca se borra físicamente (solo {@code isActive=false}): las órdenes ya tomadas
 * guardan su propia copia del nombre/precio en {@link OrdenDetalleModificador}, así que esta fila
 * puede desaparecer del catálogo sin afectarlas.
 */
@Entity
@Table(name = "modificador_opciones")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ModificadorOpcion {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grupo_id", nullable = false)
    private ModificadorGrupo grupo;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal precioExtra = BigDecimal.ZERO;

    @Column(nullable = false)
    @Builder.Default
    private Integer orden = 0;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
