package com.nexora.restopos.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Categoría de {@link Product}, propiedad de una {@link Tienda} (dato aislado por tenant,
 * no compartido entre tiendas).
 * <p>
 * Se elimina mediante borrado suave ({@code isActive=false} + {@code deletedBy}/{@code deletedAt})
 * para conservar el historial de auditoría de quién y cuándo la dio de baja.
 */
@Entity
@Table(name = "categories")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Category {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    // Horario en el que la categoría se considera disponible cuando availabilityMode=AUTO
    // (ej. "Desayuno" 10:00-12:00); null en ambos = sin horario, disponible todo el día. Los
    // dos siempre van juntos (ver CategoryService#validateSchedule) — no tiene sentido guardar
    // solo uno de los dos.
    private LocalTime scheduleStart;
    private LocalTime scheduleEnd;

    // AUTO por default: una categoría recién creada, sin horario configurado, se comporta
    // igual que antes de que existiera este campo (siempre disponible). columnDefinition
    // (no solo el default de Java) es necesario para que Hibernate pueda agregar esta
    // columna NOT NULL a la tabla `categories` ya existente, con filas de antes de este
    // campo: sin un DEFAULT a nivel SQL, Postgres rechaza el ALTER TABLE con "la columna
    // contiene valores null" — mismo motivo por el que isActive más abajo también lo usa.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20) default 'AUTO'")
    @Builder.Default
    private CategoryAvailabilityMode availabilityMode = CategoryAvailabilityMode.AUTO;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tienda_id")
    private Tienda tienda;

    // antes se borraba la fila físicamente; ahora es borrado suave, igual que
    // products/users/tiendas, para poder conservar quién y cuándo la eliminó.
    @Column(nullable = false, columnDefinition = "boolean default true")
    @Builder.Default
    private Boolean isActive = true;

    // LAZY: evita el ciclo User→Role→createdBy(User)→Role→... (User ya carga su Role en EAGER)
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

    // Calculado (no persistido) en cada serialización, con la hora del servidor — igual que
    // el resto de la app resuelve "ahora" (ver CashCutAutoCloseJob). Jackson lo expone como
    // "availableNow" en el JSON gracias a la convención isXxx() de JavaBeans, sin que el
    // frontend tenga que reimplementar esta lógica para decidir qué mostrar en Órdenes/Para
    // llevar (ver OrderDetail.jsx).
    /**
     * @return si esta categoría se considera disponible para pedirse EN ESTE MOMENTO, según
     *         {@link #availabilityMode}: {@code FORCE_VISIBLE}/{@code FORCE_HIDDEN} anulan
     *         cualquier horario; en {@code AUTO}, sin horario configurado siempre es
     *         disponible, y con horario compara la hora actual del servidor contra
     *         {@code [scheduleStart, scheduleEnd]} (soporta ventanas que cruzan medianoche,
     *         ej. 22:00–02:00).
     */
    public boolean isAvailableNow() {
        if (availabilityMode == CategoryAvailabilityMode.FORCE_VISIBLE) return true;
        if (availabilityMode == CategoryAvailabilityMode.FORCE_HIDDEN) return false;
        if (scheduleStart == null || scheduleEnd == null) return true;
        LocalTime now = LocalTime.now();
        if (!scheduleStart.isAfter(scheduleEnd)) {
            return !now.isBefore(scheduleStart) && !now.isAfter(scheduleEnd);
        }
        // ventana que cruza medianoche (ej. 22:00-02:00): disponible si es después de inicio
        // O antes de fin, no ambos a la vez.
        return !now.isBefore(scheduleStart) || !now.isAfter(scheduleEnd);
    }
}
