package com.nexora.restopos.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Configuración global del cierre automático de cortes de caja.
 * <p>
 * Es una tabla de configuración de fila única (no una entidad de negocio por tienda):
 * define a qué hora corre {@code CashCutAutoCloseJob} y si el cierre automático está
 * habilitado. Cuando el job cierra un {@link CashCut} por este medio, deja
 * {@code CashCut.closedBy} en null para indicar que lo cerró el sistema y no una persona.
 */
// Fila única (id=1) con la hora en que el job de cierre automático corre para
// TODAS las tiendas — es un horario global, no por tienda.
@Entity
@Table(name = "cash_cut_schedule")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CashCutSchedule {

    @Id
    private Long id;

    /** Hora (0-23) en la que corre el cierre automático. */
    @Column(nullable = false)
    private Integer closeHour;

    /** Minuto (0-59) en la que corre el cierre automático. */
    @Column(nullable = false)
    private Integer closeMinute;

    /** Si es {@code false}, {@code CashCutAutoCloseJob} no cierra ningún corte aunque llegue la hora configurada. */
    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = false;

    // LAZY: evita cargar en cadena el Role/Tienda de ese usuario (mismo motivo que en las demás entidades)
    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_user_id")
    private User updatedBy;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
