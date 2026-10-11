package com.nexora.restopos.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
 * Comanda de un servicio: agrupa los {@link OrdenDetalle} pedidos, sus {@link Comensal}es, y
 * (si no es para llevar) la {@link Mesa} donde se está atendiendo. Se cobra a través de una o
 * más {@link Cuenta} (total, por comensal o personalizada) hasta quedar {@code PAGADA}.
 */
@Entity
@Table(name = "ordenes")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@EntityListeners(com.nexora.restopos.service.CambiosListener.class)
public class Orden {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Bloqueo optimista: si dos personas modifican la misma orden a la vez (ej. dos
    // repartidores "Lo llevo yo", dos personas aceptando el mismo pedido web), la segunda
    // recibe un 409 en vez de pisar en silencio el cambio de la primera. "default 0" para que
    // las órdenes que ya existían tengan versión al agregar la columna.
    @Version
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private Long version;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tienda_id")
    private Tienda tienda;

    // null = orden para llevar/sin mesa
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "mesa_id")
    private Mesa mesa;

    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.EAGER)
    // null solo en un pedido web todavía sin aceptar (lo "toma" quien lo acepta, ver
    // OrdenService#aceptarPedidoWeb). La columna se vuelve nullable en WebPedidosMigration.
    @JoinColumn(name = "mesero_id")
    private User mesero;

    @Column(nullable = false)
    @Builder.Default
    private Integer numeroComensales = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private OrdenEstado estado = OrdenEstado.ABIERTA;

    @Column(columnDefinition = "TEXT")
    private String notas;

    // Solo tiene sentido para una orden para llevar (mesa == null) — opcional, capturada al
    // abrir la orden (ver OrdenCreateRequest/Takeaway.jsx), para que el mesero sepa a dónde
    // entregarla sin tener que preguntarle otra vez al cliente.
    @Column(length = 200)
    private String direccion;

    // Obligatorios para una orden para llevar (mesa == null), ver OrdenService#abrir: a quién
    // se le entrega y cómo contactarlo. Null en órdenes de mesa.
    @Column(length = 100)
    private String clienteNombre;

    @Column(length = 30)
    private String clienteTelefono;

    // De dónde vino la orden: "POS" (capturada por el personal) o "WEB" (página pública).
    // String y no enum a propósito: un enum nuevo exigiría tocar el CHECK que Hibernate generó.
    @Column(nullable = false, length = 10, columnDefinition = "varchar(10) default 'POS'")
    @Builder.Default
    private String origen = "POS";

    // Pedido web recién llegado: el personal debe aceptarlo antes de que cocina lo vea.
    @Column(nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private Boolean pendienteAceptar = false;

    // Solo pedidos web: cómo dijo el cliente que va a pagar (EFECTIVO/TARJETA/TRANSFERENCIA)
    // y si es para recoger o a domicilio (RECOGER/DOMICILIO). Informativo para el personal.
    @Column(length = 20)
    private String metodoPago;

    @Column(length = 20)
    private String tipoEntrega;

    // Solo pedidos web: código aleatorio (no adivinable) con el que el cliente consulta su
    // pedido en la página pública — ver OrdenService#estadoPedidoWeb. Nunca se expone en la API
    // del personal (JsonIgnore); al cliente se le entrega una sola vez, al crear el pedido.
    @JsonIgnore
    @Column(length = 64, unique = true)
    private String trackingToken;

    // Llave que manda la página web en cada intento de enviar un pedido (header
    // Idempotency-Key): si el mismo envío llega dos veces (doble clic, red lenta que reintenta),
    // se devuelve el pedido ya creado en vez de crear otro. Ver PublicController#crearPedido.
    @JsonIgnore
    @Column(length = 64, unique = true)
    private String idempotencyKey;

    // Solo envíos a domicilio: quién lo lleva (lo "toma" desde la pantalla Entregas) y cuándo
    // se le entregó al cliente. Ver DeliveryService.
    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "repartidor_id")
    private User repartidor;

    private LocalDateTime entregadaAt;

    // Cuándo el repartidor marcó "Voy en camino" (solo se puede después de que el restaurante
    // se lo entregó, ver DeliveryService#enCamino). A partir de aquí el cliente ve "En camino".
    private LocalDateTime enCaminoAt;

    // Cliente registrado de la página web que hizo el pedido (null = invitado o pedido de caja).
    @JsonIgnoreProperties({"tienda"})
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    // Reseña que deja el cliente registrado cuando su pedido ya terminó (ver ClienteService#resenar).
    private Integer resenaEstrellas;

    @Column(columnDefinition = "TEXT")
    private String resenaComentario;

    private LocalDateTime resenaAt;

    // Reporte de cómo llegó el pedido, del cliente registrado, dentro de las 24 h posteriores a
    // la entrega (ver ClienteService#reportar): BIEN, MAL o FALTANTE, con los productos
    // afectados (nombres separados por coma) y un comentario opcional.
    @Column(length = 20)
    private String reporteTipo;

    @Column(columnDefinition = "TEXT")
    private String reporteProductos;

    @Column(columnDefinition = "TEXT")
    private String reporteComentario;

    private LocalDateTime reporteAt;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal descuento = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal impuesto = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal total = BigDecimal.ZERO;

    // EAGER a propósito: jackson-datatype-hibernate6 serializa una colección LAZY no
    // inicializada como null en vez de cargarla, así que sin EAGER el frontend nunca vería
    // los comensales/productos de la orden.
    @JsonManagedReference
    @OneToMany(mappedBy = "orden", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<Comensal> comensales = new ArrayList<>();

    @JsonManagedReference
    @OneToMany(mappedBy = "orden", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<OrdenDetalle> items = new ArrayList<>();

    @CreationTimestamp
    private LocalDateTime openedAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    private LocalDateTime closedAt;

    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by_user_id")
    private User cancelledBy;

    private LocalDateTime cancelledAt;

    @Column(length = 255)
    private String motivoCancelacion;
}
