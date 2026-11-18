package com.nexora.restopos.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Comanda por imprimir: lo que un mesero acaba de mandar de una orden (un "Enviar a cocina" /
 * "Crear orden"). Es una cola: la "estación de impresión" (una computadora fija con QZ Tray,
 * normalmente la de caja) la consulta, toma cada comanda pendiente y la imprime — en la
 * impresora de cocina si la tienda tiene "Enviar a cocina" activo ({@code destino=COCINA}), o
 * en la impresora normal de tickets si no ({@code destino=CAJA}).
 * <p>
 * Así los meseros pueden tomar orden desde un celular (sin QZ Tray) y aun así sale impreso.
 * Se guardan solo los ids de las líneas ({@code detalleIds}); el contenido se arma al
 * imprimir, a partir de esas {@link OrdenDetalle}.
 */
@Entity
@Table(name = "comandas", indexes = @Index(name = "idx_comandas_pendientes", columnList = "tienda_id, tomada_at"))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Comanda {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tienda_id", nullable = false)
    private Long tiendaId;

    @Column(name = "orden_id", nullable = false)
    private Long ordenId;

    /** COCINA (impresora de cocina) o CAJA (impresora normal de tickets). */
    @Column(nullable = false, length = 10)
    private String destino;

    /** Ids de las {@link OrdenDetalle} que entran en esta comanda, separados por coma. */
    @Column(name = "detalle_ids", nullable = false, length = 2000)
    private String detalleIds;

    /** Nombre de quien la mandó (para el ticket). */
    @Column(name = "enviada_por", length = 100)
    private String enviadaPor;

    /** Cuándo la tomó una estación para imprimirla (null = pendiente). */
    @Column(name = "tomada_at")
    private LocalDateTime tomadaAt;

    /** Veces que se intentó imprimir (si la impresora falla se libera para reintentar). */
    @Builder.Default
    @Column(nullable = false)
    private Integer intentos = 0;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
