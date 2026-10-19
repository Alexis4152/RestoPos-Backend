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

    /** Pasa todos los productos de una orden a otra (unir mesas). */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value = "UPDATE orden_detalles SET orden_id = :destino WHERE orden_id = :origen", nativeQuery = true)
    int moverAOrden(@org.springframework.data.repository.query.Param("origen") Long origen,
                    @org.springframework.data.repository.query.Param("destino") Long destino);

    /**
     * Notas escritas en las líneas de un producto desde {@code desde} (sin lo cancelado), con
     * cuántas veces se repitió cada texto: filas {@code [nota, veces]}. Base de las "notas
     * rápidas" del modo rápido (ProductService#sugerencias), que además las separa por comas.
     */
    @Query(value = "SELECT MIN(TRIM(observaciones)), COUNT(*) FROM orden_detalles " +
                   "WHERE producto_id = :productoId AND estado <> 'CANCELADO' AND created_at >= :desde " +
                   "AND observaciones IS NOT NULL AND TRIM(observaciones) <> '' " +
                   "GROUP BY LOWER(TRIM(observaciones)) ORDER BY COUNT(*) DESC LIMIT 200", nativeQuery = true)
    List<Object[]> contarNotas(@Param("productoId") Long productoId, @Param("desde") LocalDateTime desde);
}
