package com.nexora.restopos.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Cuenta a cobrar de una {@link Orden}: agrupa un subconjunto de sus {@link OrdenDetalle}
 * (vía {@link CuentaDetalle}) según el {@link CuentaTipo} elegido, y acumula los {@link Pago}
 * que la liquidan. Es el "ticket" de esa parte de la cuenta: {@link #ticketFolio} identifica
 * el comprobante generado en PDF.
 */
@Entity
@Table(name = "cuentas")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Cuenta {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "orden_id", nullable = false)
    private Orden orden;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private CuentaTipo tipo;

    // solo aplica cuando tipo = POR_COMENSAL
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "comensal_id")
    private Comensal comensal;

    // Incluye la propina (ver `propina` abajo): es el importe real a cobrar de esta cuenta,
    // el mismo que valida PagoService contra la suma de pagos.
    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal total = BigDecimal.ZERO;

    // Propina ya sumada a `total` — se guarda aparte solo para desglosarla en el ticket y en
    // reportes, nunca se factura como si fuera un producto.
    @Column(precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal propina = BigDecimal.ZERO;

    // % usado para calcular `propina`, si se capturó como porcentaje — null si fue un monto
    // fijo o si no hay propina. Solo informativo (para mostrar "Propina (10%)" en el ticket).
    @Column(precision = 5, scale = 2)
    private BigDecimal propinaPorcentaje;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalPagado = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    @Builder.Default
    private CuentaEstado estado = CuentaEstado.PENDIENTE;

    // EAGER a propósito: jackson-datatype-hibernate6 serializa una colección LAZY no
    // inicializada como null en vez de cargarla (ver la misma nota en Orden).
    @JsonManagedReference
    @OneToMany(mappedBy = "cuenta", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<CuentaDetalle> items = new ArrayList<>();

    @JsonManagedReference
    @OneToMany(mappedBy = "cuenta", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<Pago> pagos = new ArrayList<>();

    // folio legible del ticket ("C-<id>"); se asigna tras el primer guardado, cuando ya hay id
    @Column(length = 30)
    private String ticketFolio;

    @Column(nullable = false)
    @Builder.Default
    private Boolean ticketEmailEnviado = false;

    @Column(length = 150)
    private String ticketEmailDestino;

    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @CreationTimestamp
    private LocalDateTime createdAt;

    private LocalDateTime closedAt;
}
