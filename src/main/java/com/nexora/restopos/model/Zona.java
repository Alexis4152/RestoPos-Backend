package com.nexora.restopos.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Zona física del restaurante (ej. "Zona principal", "Terraza", "Barra"), propiedad de una
 * {@link Tienda}, usada para agrupar {@link Mesa}s. Sin límite de cantidad ni de mesas por
 * zona — es solo una etiqueta organizativa que el ADMIN crea/edita libremente, igual que una
 * {@link Category} de productos.
 * <p>
 * Se elimina mediante borrado suave ({@code isActive=false} + {@code deletedBy}/{@code deletedAt})
 * para conservar el historial de auditoría, igual que el resto de los catálogos de la app.
 */
@Entity
@Table(name = "zonas")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Zona {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tienda_id")
    private Tienda tienda;

    @Column(nullable = false, columnDefinition = "boolean default true")
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

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
