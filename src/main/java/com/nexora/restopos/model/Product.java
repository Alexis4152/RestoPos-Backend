package com.nexora.restopos.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Producto vendible del catálogo de una {@link Tienda} (dato aislado por tenant).
 * <p>
 * El {@code stock} se actualiza automáticamente con cada venta, cancelación de venta,
 * o movimiento manual, y cada cambio queda registrado como un {@link InventoryMovement}.
 * Se elimina mediante borrado suave ({@code isActive=false} + {@code deletedBy}/{@code deletedAt})
 * para conservar el historial de auditoría y no romper la referencia desde ventas pasadas.
 */
@Entity
@Table(name = "products")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Product {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Código de barras del producto, usado para búsqueda rápida en el punto de venta. */
    @Column(length = 100)
    private String barcode;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal price = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal cost = BigDecimal.ZERO;

    @Column(nullable = false)
    @Builder.Default
    private Integer stock = 0;

    /** Umbral mínimo de stock; por debajo de este valor el producto se considera con inventario bajo. */
    @Column(nullable = false)
    @Builder.Default
    private Integer minStock = 0;

    /** Unidad de medida en la que se vende el producto (ej. "pza", "kg", "lt"). */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String unit = "pza";

    @Column(length = 255)
    private String imageUrl;

    /**
     * Si el producto lleva inventario (ej. refrescos, cervezas): solo estos aparecen en
     * Inventario, en las alertas de stock bajo y en "buscar en tiendas asociadas", y solo a
     * estos se les puede ajustar stock. Un platillo preparado normalmente no lo lleva.
     */
    @Column(nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private Boolean controlaInventario = false;

    /**
     * Lleva inventario y no le queda stock: no se puede vender (InventarioVentaService). En el
     * POS se muestra "Sin inventario"; en la página web, "No disponible". Calculado, no se guarda.
     */
    @com.fasterxml.jackson.annotation.JsonProperty("sinInventario")
    public boolean isSinInventario() {
        return Boolean.TRUE.equals(controlaInventario) && stock != null && stock <= 0;
    }

    /** Disponibilidad temporal (ej. "se acabó por hoy"), distinta de {@code isActive} (baja permanente). */
    @Column(nullable = false, columnDefinition = "boolean default true")
    @Builder.Default
    private Boolean disponible = true;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tienda_id")
    private Tienda tienda;

    // Grupos de modificadores configurables del producto (ej. "Término de cocción", "Extras") —
    // incluye los inactivos (borrados suaves); el frontend filtra por isActive al armar el
    // selector de la toma de órdenes. EAGER a propósito, igual que el resto de estas colecciones:
    // así el catálogo (GET /products, /search, /top-selling) ya trae los grupos de un jalón, sin
    // una llamada aparte por producto.
    @JsonManagedReference
    @OneToMany(mappedBy = "producto", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.EAGER)
    @OrderBy("orden ASC, id ASC")
    @Builder.Default
    private List<ModificadorGrupo> modificadorGrupos = new ArrayList<>();

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    // LAZY: User también carga su Role/Tienda en EAGER, y evitar el ciclo
    // User→Role→createdBy(User)→Role→... es más simple que ir entidad por entidad.
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
