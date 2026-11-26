package com.nexora.restopos.repository;

import com.nexora.restopos.model.Cuenta;
import com.nexora.restopos.model.CuentaEstado;
import com.nexora.restopos.model.PaymentMethod;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface CuentaRepository extends JpaRepository<Cuenta, Long> {
    List<Cuenta> findByOrdenIdOrderByCreatedAtAsc(Long ordenId);

    /** Bloquea la fila hasta terminar la transacción: dos cobros a la misma cuenta van uno tras otro. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Cuenta c WHERE c.id = :id")
    java.util.Optional<Cuenta> findByIdForUpdate(@Param("id") Long id);

    /** Cuenta por el código del QR "califica tu visita" (ver OpinionService). */
    java.util.Optional<Cuenta> findByOpinionToken(String opinionToken);

    /**
     * Búsqueda paginada de cuentas (ventas) con filtros combinables, para el módulo de
     * Ventas. {@code tiendaId} nulo indica SUPER_ADMIN viendo todas las tiendas; el resto de
     * los filtros nulos no filtran. {@code DISTINCT} evita duplicados cuando una cuenta tiene
     * varios pagos (pago mixto) y se filtra por método de pago.
     */
    @Query("SELECT DISTINCT c FROM Cuenta c JOIN c.orden o LEFT JOIN c.pagos p WHERE " +
           "(:tiendaId IS NULL OR o.tienda.id = :tiendaId) " +
           "AND c.createdAt BETWEEN :from AND :to " +
           "AND (:estado IS NULL OR c.estado = :estado) " +
           "AND (:metodo IS NULL OR (p.metodo = :metodo AND p.cancelado = false)) " +
           "ORDER BY c.createdAt DESC")
    Page<Cuenta> search(@Param("tiendaId") Long tiendaId,
                         @Param("from") LocalDateTime from,
                         @Param("to") LocalDateTime to,
                         @Param("estado") CuentaEstado estado,
                         @Param("metodo") PaymentMethod metodo,
                         Pageable pageable);

    // Módulo de Ventas (ver CuentaService#searchVentas): una venta es una orden con al menos una
    // cuenta dentro del filtro de fecha/método. Todo se resuelve en SQL, incluida la página, para
    // no cargar el historial completo en memoria en cada consulta.
    //
    // Estado agregado de la orden (mismo criterio que CuentaService#calcularEstadoAgregado):
    // CANCELADA si todas sus cuentas están canceladas; PAGADA si todas las no canceladas se
    // cobraron; PENDIENTE en cualquier otro caso.
    String ESTADO_AGREGADO =
            "(SELECT CASE WHEN COUNT(*) FILTER (WHERE ce.estado <> 'CANCELADA') = 0 THEN 'CANCELADA' " +
            " WHEN COUNT(*) FILTER (WHERE ce.estado NOT IN ('CANCELADA', 'PAGADA')) = 0 THEN 'PAGADA' " +
            " ELSE 'PENDIENTE' END FROM cuentas ce WHERE ce.orden_id = o.id)";
    // Enums como texto (name()) para que PostgreSQL no tenga que adivinar el tipo de un null.
    String VENTAS_WHERE =
            "WHERE (CAST(:tiendaId AS bigint) IS NULL OR o.tienda_id = :tiendaId) " +
            "AND o.id IN (SELECT c.orden_id FROM cuentas c LEFT JOIN pagos p ON p.cuenta_id = c.id " +
            "  WHERE c.created_at BETWEEN :from AND :to " +
            "  AND (CAST(:metodo AS varchar) IS NULL OR (p.metodo = CAST(:metodo AS varchar) AND p.cancelado = false))) " +
            "AND (CAST(:estado AS varchar) IS NULL OR " + ESTADO_AGREGADO + " = CAST(:estado AS varchar)) ";

    /**
     * Ids de las órdenes de una página de Ventas, de la cuenta más reciente a la más antigua.
     * {@code estado} y {@code metodo} van como texto ({@code name()} del enum) o null.
     */
    @Query(nativeQuery = true, value = "SELECT o.id FROM ordenes o " + VENTAS_WHERE +
            "ORDER BY (SELECT MAX(cf.created_at) FROM cuentas cf WHERE cf.orden_id = o.id) DESC, o.id DESC " +
            "LIMIT :limit OFFSET :offset")
    List<Long> searchVentaIds(@Param("tiendaId") Long tiendaId,
                              @Param("from") LocalDateTime from,
                              @Param("to") LocalDateTime to,
                              @Param("estado") String estado,
                              @Param("metodo") String metodo,
                              @Param("limit") int limit,
                              @Param("offset") long offset);

    /** Total de ventas (órdenes) que cumplen el mismo filtro que {@link #searchVentaIds}. */
    @Query(nativeQuery = true, value = "SELECT COUNT(*) FROM ordenes o " + VENTAS_WHERE)
    long countVentas(@Param("tiendaId") Long tiendaId,
                     @Param("from") LocalDateTime from,
                     @Param("to") LocalDateTime to,
                     @Param("estado") String estado,
                     @Param("metodo") String metodo);

    /** Cuentas por cobrar generadas desde {@code desde} (avisos de la caja), más recientes primero. */
    @Query("SELECT c FROM Cuenta c JOIN c.orden o WHERE (:tiendaId IS NULL OR o.tienda.id = :tiendaId) " +
           "AND c.estado = com.nexora.restopos.model.CuentaEstado.PENDIENTE AND c.createdAt >= :desde " +
           "ORDER BY c.createdAt DESC")
    List<Cuenta> findPendientesDesde(@Param("tiendaId") Long tiendaId, @Param("desde") LocalDateTime desde);

    /** Todas las cuentas de las órdenes dadas, para armar el desglose de {@code searchVentas}. */
    List<Cuenta> findByOrdenIdInOrderByCreatedAtAsc(List<Long> ordenIds);
}
