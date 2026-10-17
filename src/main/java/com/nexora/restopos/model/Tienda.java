package com.nexora.restopos.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Tienda (sucursal/negocio) del sistema multi-tenant: la unidad de aislamiento a la que
 * pertenecen usuarios, roles, productos, categorías, ventas y cortes de caja.
 * <p>
 * Salvo el usuario de plataforma SUPER_ADMIN (que no pertenece a ninguna tienda y ve todo,
 * ver {@code TenantScope}), cada dato de negocio queda acotado a su {@code tienda}, y las
 * consultas se filtran por {@code tienda.id} para garantizar el aislamiento entre negocios.
 * <p>
 * {@code primaryColor} y {@code logoPath} permiten personalizar la apariencia del portal
 * para esta tienda (color de marca y logo); los datos fiscales/de contacto usados en el
 * ticket impreso viven aparte, en {@link TiendaInfo}.
 * <p>
 * Se elimina mediante borrado suave ({@code isActive=false} + {@code deletedBy}/{@code deletedAt}).
 */
@Entity
@Table(name = "tiendas")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Tienda {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    // color de marca elegido por el ADMIN de esta tienda (hex, ej. "#155dea");
    // null = usa el azul Nexora por default en el frontend
    @Column(length = 7)
    private String primaryColor;

    // ruta pública del logo subido por el ADMIN de esta tienda (ej. "/uploads/logos/tienda-1-xxx.png");
    // null = usa el logo de Nexora por default en el frontend
    @Column(length = 255)
    private String logoPath;

    // Usuario de plataforma con rol SUPERVISOR a cargo de esta tienda (null = sin supervisor
    // asignado, o la tienda solo la administra SUPER_ADMIN directamente). Un SUPERVISOR puede
    // tener varias tiendas a su cargo; ver TenantScope para cómo esto acota su visibilidad.
    // LAZY + JsonIgnoreProperties por el mismo motivo que createdBy/updatedBy/deletedBy: evita
    // que Jackson expanda su role/tienda al serializar (User→role→tienda→... sería un ciclo).
    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supervisor_id")
    private User supervisor;

    // LAZY: evita el ciclo User→Tienda(EAGER)→createdBy(User)→Tienda→... (User carga su Tienda en EAGER).
    // JsonIgnoreProperties evita además que, al serializar, Jackson expanda role/tienda de
    // este User anidado — sin esto, Tienda.updatedBy→role→tienda→updatedBy→... es un ciclo
    // infinito real (lo que rompía /api/auth/login y /api/auth/me en producción).
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

    // Conteo ciego al cerrar corte: el cajero (no un ADMIN/SUPERVISOR) no ve el efectivo
    // esperado ni sus ventas en efectivo mientras su corte está abierto; cuenta, cierra y
    // solo entonces ve la diferencia — así no puede "ajustar" su conteo para que cuadre.
    // Opcional por tienda ("Datos de la tienda"), apagado por default.
    @Column(nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private Boolean blindCashCount = false;

    // El supervisor (dueño) recibe el reporte de fin de día de cada caja por default; puede
    // desactivarlo si prefiere que solo les llegue a los destinatarios que configuró (ver
    // CashReportRecipient / CashReportRecipientService).
    @Column(nullable = false, columnDefinition = "boolean default true")
    @Builder.Default
    private Boolean reportToSupervisor = true;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
