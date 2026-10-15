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

    // Usado por el módulo de Ventas (ver CuentaService#searchVentas) para agrupar por orden:
    // primero se resuelven los ids de las órdenes que tienen al menos una cuenta dentro del
    // filtro de fecha/método (el estado no se filtra aquí porque es una propiedad agregada de
    // TODAS las cuentas de la orden, no de una cuenta suelta — se calcula y filtra en Java).
    /** Ids de órdenes (sin duplicados) con al menos una cuenta que cumple fecha/método, más recientes primero. */
    @Query("SELECT DISTINCT o.id FROM Cuenta c JOIN c.orden o LEFT JOIN c.pagos p WHERE " +
           "(:tiendaId IS NULL OR o.tienda.id = :tiendaId) " +
           "AND c.createdAt BETWEEN :from AND :to " +
           "AND (:metodo IS NULL OR (p.metodo = :metodo AND p.cancelado = false)) " +
           "ORDER BY o.id DESC")
    List<Long> searchOrdenIds(@Param("tiendaId") Long tiendaId,
                               @Param("from") LocalDateTime from,
                               @Param("to") LocalDateTime to,
                               @Param("metodo") PaymentMethod metodo);

    /** Todas las cuentas de las órdenes dadas, para armar el desglose de {@code searchVentas}. */
    List<Cuenta> findByOrdenIdInOrderByCreatedAtAsc(List<Long> ordenIds);
}
