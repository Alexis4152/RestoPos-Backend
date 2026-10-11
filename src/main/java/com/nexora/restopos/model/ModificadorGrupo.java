package com.nexora.restopos.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Grupo de modificadores de un {@link Product} (ej. "Término de cocción", "Extras"): agrupa sus
 * {@link ModificadorOpcion} bajo una regla de selección ({@link ModificadorGrupoTipo}) y si es
 * obligatorio elegir algo de él antes de poder agregar el producto a una orden — ver
 * {@code OrdenService#agregarItem}, que valida esta regla al momento de pedir.
 *
 * <p>Borrado suave, igual que Category/Mesa/Zona: un grupo eliminado no afecta las órdenes que
 * ya lo usaron, porque {@link OrdenDetalleModificador} guarda una copia de nombre/precio, no una
 * referencia viva a la opción elegida.</p>
 */
@Entity
@Table(name = "modificador_grupos")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ModificadorGrupo {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", nullable = false)
    private Product producto;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ModificadorGrupoTipo tipo;

    @Column(nullable = false)
    @Builder.Default
    private Boolean obligatorio = false;

    @Column(nullable = false)
    @Builder.Default
    private Integer orden = 0;

    // Sin orphanRemoval: quitar una opción de este grupo la desactiva (ModificadorGrupoService),
    // nunca la borra físicamente — igual que el resto del catálogo (ver Product.isActive).
    @JsonManagedReference
    @OneToMany(mappedBy = "grupo", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.EAGER)
    @OrderBy("orden ASC, id ASC")
    @Builder.Default
    private List<ModificadorOpcion> opciones = new ArrayList<>();

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

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
