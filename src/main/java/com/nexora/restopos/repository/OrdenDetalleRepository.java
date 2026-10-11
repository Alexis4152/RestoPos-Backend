package com.nexora.restopos.repository;

import com.nexora.restopos.model.OrdenDetalle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OrdenDetalleRepository extends JpaRepository<OrdenDetalle, Long> {

    List<OrdenDetalle> findByOrdenId(Long ordenId);

    // Usado para bloquear la eliminación de un Comensal que ya tiene algún producto
    // asociado (activo o cancelado) — ver OrdenService#eliminarComensal.
    boolean existsByComensalId(Long comensalId);

    /**
     * Top de productos más pedidos entre dos fechas (por cantidad), acotado a tienda.
     * Nativa porque agrega y ordena sobre columnas de dos tablas relacionadas (orden_detalles/ordenes).
     */
    @Query(value = "SELECT od.producto_nombre AS nombre, SUM(od.cantidad) AS cantidad, SUM(od.subtotal) AS total " +
                    "FROM orden_detalles od JOIN ordenes o ON o.id = od.orden_id " +
                    "WHERE o.opened_at BETWEEN :from AND :to AND od.estado <> 'CANCELADO' AND od.cargo_extra = false " +
                    "AND (:tiendaId IS NULL OR o.tienda_id = :tiendaId) " +
                    "GROUP BY od.producto_nombre ORDER BY cantidad DESC LIMIT :limit", nativeQuery = true)
    List<Object[]> topProductosBetween(@Param("from") LocalDateTime from,
                                        @Param("to") LocalDateTime to,
                                        @Param("limit") int limit,
                                        @Param("tiendaId") Long tiendaId);
}
