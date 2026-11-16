package com.nexora.restopos.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Menú con nombre de una tienda ("Entre semana", "Fin de semana", "Cuaresma"...): una selección
 * de platillos del catálogo. A lo más UNO por tienda está marcado como el de hoy ({@link #hoy});
 * mientras haya uno, solo sus platillos se pueden ordenar (POS y página web, ver
 * MenuDiaService#enMenuHoy). Sin menú de hoy se vende todo el catálogo activo, como antes.
 * Se borra de verdad (no suave): no lo referencia ninguna orden.
 */
@Entity
@Table(name = "menus", indexes = @Index(name = "idx_menus_tienda", columnList = "tienda_id"))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class MenuDia {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tienda_id", nullable = false)
    private Tienda tienda;

    /** Es el menú de hoy de su tienda (a lo más uno por tienda). */
    @Column(nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private Boolean hoy = false;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "menu_productos",
            joinColumns = @JoinColumn(name = "menu_id"),
            inverseJoinColumns = @JoinColumn(name = "product_id"))
    @Builder.Default
    private Set<Product> productos = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_user_id")
    private User updatedBy;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
