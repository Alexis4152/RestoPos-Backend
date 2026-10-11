package com.nexora.restopos.repository;

import com.nexora.restopos.model.CashCutStatus;
import com.nexora.restopos.model.CashCut;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio de {@link CashCut} (corte de caja).
 *
 * <p>Cada cajero/vendedor abre su propio corte diario con un fondo inicial; a diferencia
 * de otras entidades, no existe "el corte de la tienda" sino que varios cortes pueden
 * estar en estado {@code OPEN} simultáneamente dentro de la misma tienda, uno por usuario
 * (de ahí métodos como {@link #findFirstByUserIdAndStatus}). Un corte se cierra manualmente
 * por el propio cajero o de forma automática mediante el job programado de cierre
 * (ver {@link CashCutScheduleRepository}); cuando el cierre lo hace el job, el campo
 * {@code closedBy} de la entidad queda en {@code null}.
 */
public interface CashCutRepository extends JpaRepository<CashCut, Long> {

    /** Lista todos los cortes ordenados por fecha de apertura descendente (más reciente primero). */
    Page<CashCut> findAllByOrderByOpenedAtDesc(Pageable pageable);

    /** Indica si el usuario ya tiene un corte abierto dentro del rango de fechas dado. */
    boolean existsByUserIdAndOpenedAtBetween(Long userId, LocalDateTime from, LocalDateTime to);

    /** Obtiene el corte más reciente del usuario, abierto dentro del rango de fechas dado. */
    Optional<CashCut> findFirstByUserIdAndOpenedAtBetweenOrderByOpenedAtDesc(Long userId, LocalDateTime from, LocalDateTime to);

    // el corte propio de cada quien — varios pueden estar OPEN a la vez en la misma tienda
    /** Obtiene el corte activo (abierto) de un usuario en particular, si existe. */
    Optional<CashCut> findFirstByUserIdAndStatus(Long userId, CashCutStatus status);

    // usado por el job de cierre automático: todos los cortes abiertos, de cualquier tienda
    /** Lista todos los cortes en el estado dado, sin filtrar por tienda; usado por el job de cierre automático. */
    List<CashCut> findAllByStatus(CashCutStatus status);

    // usado por el job de cierre automático para armar el reporte del día completo:
    // todos los cortes ya cerrados hoy, sin importar si se cerraron a mano antes o los cerró el job.
    /** Lista los cortes en el estado dado abiertos dentro del rango de fechas; usado por el job de cierre automático para armar el reporte diario. */
    List<CashCut> findAllByStatusAndOpenedAtBetween(CashCutStatus status, LocalDateTime from, LocalDateTime to);

    // from/to siempre vienen con un valor real (nunca null) — ver CashCutService.findAll.
    /**
     * Búsqueda paginada de cortes de caja con filtros combinables.
     *
     * <p>{@code tiendaId} nulo indica SUPER_ADMIN viendo todas las tiendas (no se filtra por tienda);
     * cualquier otro valor restringe el resultado a esa tienda. {@code status} es opcional
     * (nulo = cualquier estado). {@code from}/{@code to} siempre llegan con un valor real desde
     * el service (nunca {@code null}), ya que Postgres no puede inferir el tipo de un parámetro
     * {@link LocalDateTime} nulo, por lo que aquí se usa {@code BETWEEN} directo en vez del patrón
     * {@code (:from IS NULL OR ...)}.
     */
    @Query("SELECT c FROM CashCut c WHERE " +
           "(:tiendaId IS NULL OR c.tienda.id = :tiendaId) " +
           "AND c.openedAt BETWEEN :from AND :to " +
           "AND (:status IS NULL OR c.status = :status) " +
           "ORDER BY c.openedAt DESC")
    Page<CashCut> search(@Param("tiendaId") Long tiendaId,
                          @Param("from") LocalDateTime from,
                          @Param("to") LocalDateTime to,
                          @Param("status") CashCutStatus status,
                          Pageable pageable);
}
