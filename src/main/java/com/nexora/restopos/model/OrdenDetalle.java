package com.nexora.restopos.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Línea de una {@link Orden}: un producto, su cantidad y, opcionalmente, a qué {@link Comensal}
 * pertenece (null = compartido entre todos). {@code cantidadFacturada} lleva cuánto de esta
 * línea ya se incluyó en alguna {@link Cuenta}, para nunca cobrar dos veces el mismo producto;
 * {@code version} habilita bloqueo optimista para que dos cobros simultáneos sobre la misma
 * línea no puedan ambos asumir que había cantidad disponible.
 */
@Entity
@Table(name = "orden_detalles")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@EntityListeners(com.nexora.restopos.service.CambiosListener.class)
public class OrdenDetalle {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orden_id", nullable = false)
    private Orden orden;

    // null = compartido por toda la mesa
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "comensal_id")
    private Comensal comensal;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "producto_id")
    private Product producto;

    // copia del nombre al momento de pedir: el ticket no debe cambiar si el producto se renombra después
    @Column(nullable = false, length = 200)
    private String productoNombre;

    @Column(nullable = false, precision = 10, scale = 3)
    @Builder.Default
    private BigDecimal cantidad = BigDecimal.ONE;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precioUnitario;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal descuento = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(columnDefinition = "TEXT")
    private String observaciones;

    // Modificadores elegidos al pedir (ej. "Término: Bien cocido", "Extra: Tocino") — su
    // precioExtra ya está incluido en `subtotal`, aquí solo se guardan para desglosarlos en el
    // ticket/comanda. orphanRemoval borra los anteriores cuando OrdenService#editarItem
    // reemplaza los modificadores de la línea.
    @JsonManagedReference
    @OneToMany(mappedBy = "ordenDetalle", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<OrdenDetalleModificador> modificadores = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private OrdenDetalleEstado estado = OrdenDetalleEstado.PENDIENTE;

    @Column(nullable = false, precision = 10, scale = 3, columnDefinition = "numeric(10,3) default 0")
    @Builder.Default
    private BigDecimal cantidadFacturada = BigDecimal.ZERO;

    // true = no es un platillo sino un cargo agregado a la orden (hoy: envío a domicilio, ver
    // OrdenService#abrir). Se cobra como cualquier línea, pero cocina no lo ve ni se edita.
    @Column(nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private Boolean cargoExtra = false;

    // Cuándo salió de cocina (primera vez que pasó a ENTREGADO_MESERO o ENTREGADO). Lo usa la
    // vista "Ya entregados" de Cocina para mostrar solo lo de hoy (ver OrdenService#findEntregadosHoy).
    private LocalDateTime entregadoCocinaAt;

    @Version
    private Long version;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
