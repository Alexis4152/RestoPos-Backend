package com.nexora.restopos.repository;

import com.nexora.restopos.model.CashCutStatus;
import com.nexora.restopos.model.CashCut;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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
           "AND (:userId IS NULL OR c.user.id = :userId) " +
           "AND (:arqueoStatus IS NULL OR c.arqueoStatus = :arqueoStatus) " +
           "ORDER BY c.openedAt DESC")
    Page<CashCut> search(@Param("tiendaId") Long tiendaId,
                          @Param("from") LocalDateTime from,
                          @Param("to") LocalDateTime to,
                          @Param("status") CashCutStatus status,
                          @Param("userId") Long userId,
                          @Param("arqueoStatus") com.nexora.restopos.model.HandoverStatus arqueoStatus,
                          Pageable pageable);

    @Query("SELECT COUNT(c) FROM CashCut c WHERE (:tiendaId IS NULL OR c.tienda.id = :tiendaId) " +
           "AND c.arqueoStatus = com.nexora.restopos.model.HandoverStatus.DIFFERENCE")
    long countUnresolvedArqueos(@Param("tiendaId") Long tiendaId);

    /**
     * Cajeros distintos que tienen al menos un corte de caja en la tienda dada — para
     * poblar el filtro "Cajero" del historial ({@link #search}), sin depender de la
     * sección {@code USERS} (que un ADMIN podría no tener habilitada) como haría
     * {@code UserController}. {@code tiendaId} nulo indica SUPER_ADMIN viendo todas las
     * tiendas (no se filtra por tienda).
     */
    // Fin de día: cortes de una caja cerrados después de su último fin de día.
    @Query("SELECT c FROM CashCut c WHERE c.cashRegister.id = :registerId " +
           "AND c.status = com.nexora.restopos.model.CashCutStatus.CLOSED AND c.closedAt > :from ORDER BY c.closedAt ASC")
    List<CashCut> findClosedByRegisterSince(@Param("registerId") Long registerId, @Param("from") LocalDateTime from);

    // Cortes sin caja (anteriores a las cajas) cerrados en un rango: los reporta el job por tienda.
    List<CashCut> findByCashRegisterIsNullAndStatusAndClosedAtBetween(CashCutStatus status, LocalDateTime from, LocalDateTime to);

    // la caja solo puede tener un corte abierto a la vez
    Optional<CashCut> findFirstByCashRegisterIdAndStatus(Long cashRegisterId, CashCutStatus status);

    /** Bloqueo pesimista para que dos salidas simultáneas no saquen más efectivo del que hay. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CashCut c WHERE c.id = :id")
    Optional<CashCut> findByIdForUpdate(@Param("id") Long id);

    // "Últimas entregas de caja": cada corte que recibió una caja de un turno anterior.
    @Query("SELECT c FROM CashCut c WHERE (:tiendaId IS NULL OR c.tienda.id = :tiendaId) " +
           "AND c.cashRegister IS NOT NULL AND c.expectedOpeningAmount IS NOT NULL " +
           "ORDER BY c.openedAt DESC")
    Page<CashCut> findHandovers(@Param("tiendaId") Long tiendaId, Pageable pageable);

    @Query("SELECT COUNT(c) FROM CashCut c WHERE (:tiendaId IS NULL OR c.tienda.id = :tiendaId) " +
           "AND c.handoverStatus = com.nexora.restopos.model.HandoverStatus.DIFFERENCE")
    long countUnresolvedHandovers(@Param("tiendaId") Long tiendaId);

    @Query("SELECT DISTINCT c.user.id, c.user.name FROM CashCut c " +
           "WHERE (:tiendaId IS NULL OR c.tienda.id = :tiendaId) " +
           "ORDER BY c.user.name ASC")
    List<Object[]> findDistinctCashiers(@Param("tiendaId") Long tiendaId);

    /** ¿La tienda tiene algún corte abierto ahora? (resumen de la pantalla de tiendas). */
    boolean existsByTiendaIdAndStatus(Long tiendaId, CashCutStatus status);
}
