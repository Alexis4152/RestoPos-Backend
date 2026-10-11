package com.nexora.restopos.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Porción de un {@link OrdenDetalle} incluida en una {@link Cuenta}. Existe para poder dividir
 * una línea con cantidad mayor a 1 entre distintas cuentas sin cobrar dos veces la misma unidad
 * (la suma de {@code cantidad} de todos los {@link CuentaDetalle} de una línea nunca puede
 * superar la {@code cantidad} de esa línea — lo garantiza {@code OrdenDetalle.cantidadFacturada}).
 */
@Entity
@Table(name = "cuenta_detalles")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CuentaDetalle {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuenta_id", nullable = false)
    private Cuenta cuenta;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "orden_detalle_id", nullable = false)
    private OrdenDetalle ordenDetalle;

    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal cantidad;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;
}
