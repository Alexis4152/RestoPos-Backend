package com.nexora.restopos.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Rol de acceso (RBAC) que agrupa un conjunto de {@link AppSection} a las que da acceso.
 * <p>
 * A diferencia de otros catálogos, un rol pertenece a una sola {@link Tienda} (con la
 * excepción del rol de plataforma SUPER_ADMIN, cuya {@code tienda} es null): no es global
 * ni se comparte entre tiendas, cada tienda administra sus propios roles de forma
 * independiente. El nombre es único por tienda ({@code roles.tienda_id + name}).
 * <p>
 * Se elimina mediante borrado suave ({@code isActive=false} + {@code deletedBy}/{@code deletedAt});
 * antes se borraba la fila físicamente.
 */
@Entity
@Table(name = "roles", uniqueConstraints = @UniqueConstraint(columnNames = {"tienda_id", "name"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Role {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String name;

    @Column(length = 200)
    private String description;

    /** Marca los roles predefinidos creados por el sistema (ej. ADMIN, VENDEDOR), no editables/borrables por el usuario. */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isSystem = false;

    // null = rol de plataforma (SUPER_ADMIN); todo lo demás pertenece a una sola tienda
    // y no se comparte ni se ve afectado por cambios en otras tiendas.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tienda_id")
    private Tienda tienda;

    /** Secciones de la app ({@link AppSection}) a las que este rol da acceso. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_sections", joinColumns = @JoinColumn(name = "role_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "section", nullable = false, length = 20)
    @Builder.Default
    private Set<AppSection> sections = new HashSet<>();

    // antes se borraba la fila físicamente; ahora es borrado suave, igual que
    // products/users/tiendas/categories, para poder conservar quién y cuándo lo eliminó.
    @Column(nullable = false, columnDefinition = "boolean default true")
    @Builder.Default
    private Boolean isActive = true;

    // LAZY: evita el ciclo User→Role(EAGER)→createdBy(User)→Role→... (User carga su Role en EAGER)
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
