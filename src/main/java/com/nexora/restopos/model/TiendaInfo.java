package com.nexora.restopos.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Datos fiscales y de contacto de una {@link Tienda} (RFC, razón social, dirección,
 * teléfono, redes sociales, etc.), usados principalmente para imprimirse en el ticket
 * PDF de las ventas.
 * <p>
 * Relación uno a uno con {@link Tienda} ({@code tienda_id} es único); es un registro
 * opcional que se captura desde la pantalla de "Datos de la tienda", no en el alta inicial.
 */
// Datos fiscales/de contacto de la tienda (RFC, dirección, etc.) — separados de la
// entidad Tienda para no cargar esos campos en cada lugar que ya serializa Tienda
// (login, ventas, cortes...). Se piden solo desde la pantalla de "Datos de la tienda".
@Entity
@Table(name = "tienda_info", uniqueConstraints = @UniqueConstraint(columnNames = "tienda_id"))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TiendaInfo {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tienda_id", nullable = false)
    private Tienda tienda;

    @Column(length = 20)
    private String rfc;

    // Dirección separada por campo (no un solo texto libre) para que el ticket
    // la pueda imprimir ordenada, un renglón por dato: calle, colonia, C.P., localidad, estado.
    @Column(length = 200)
    private String calle;

    @Column(length = 150)
    private String colonia;

    @Column(length = 10)
    private String codigoPostal;

    @Column(length = 150)
    private String localidad;

    @Column(length = 100)
    private String estado;

    /** Razón social de la tienda para efectos fiscales/facturación. */
    @Column(length = 200)
    private String razonSocial;

    @Column(length = 30)
    private String telefono;

    @Column(length = 200)
    private String paginaWeb;

    /** Enlaces o menciones a redes sociales de la tienda, en texto libre. */
    @Column(columnDefinition = "TEXT")
    private String redesSociales;

    // campo libre para lo que no entra en las demás columnas
    @Column(columnDefinition = "TEXT")
    private String notasAdicionales;

    // Cargo que se suma a una orden para llevar marcada como "envío a domicilio" (ver
    // OrdenService#abrir). Null o 0 = la tienda no cobra envío / no lo ha configurado.
    @Column(precision = 12, scale = 2)
    private BigDecimal costoEnvioDomicilio;

    // ── Página web pública (ver PublicController / WebConfigRequest) ──────────────────────
    // Sitio para clientes en /sitio/{publicSlug}: menú, mesas libres/ocupadas y pedidos para
    // recoger o a domicilio. Todo lo configura el admin de la tienda; apagada por default.
    @Column(nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private Boolean webEnabled = false;

    // Parte variable del link público (ej. "tacos-don-pepe"); único entre todas las tiendas.
    @Column(length = 80, unique = true)
    private String publicSlug;

    @Column(nullable = false, columnDefinition = "boolean default true")
    @Builder.Default
    private Boolean webMostrarMesas = true;

    @Column(nullable = false, columnDefinition = "boolean default true")
    @Builder.Default
    private Boolean webRecogerEnabled = true;

    @Column(nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private Boolean webDomicilioEnabled = false;

    @Column(nullable = false, columnDefinition = "boolean default true")
    @Builder.Default
    private Boolean webPagoEfectivo = true;

    @Column(nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private Boolean webPagoTarjeta = false;

    @Column(nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private Boolean webPagoTransferencia = false;

    // Datos que se le muestran al cliente que elige pagar por transferencia.
    @Column(length = 100)
    private String transferenciaBanco;

    @Column(length = 150)
    private String transferenciaTitular;

    @Column(length = 30)
    private String transferenciaClabe;

    // Texto libre que se muestra en la portada del sitio (ej. horario, promociones).
    @Column(columnDefinition = "TEXT")
    private String webMensaje;

    // LAZY: evita cargar en cadena Role/Tienda/etc. de ese usuario (mismo motivo que en las demás entidades)
    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_user_id")
    private User updatedBy;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
