package com.nexora.restopos.repository;

import com.nexora.restopos.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio de {@link Product}.
 *
 * <p>El borrado de un producto es siempre suave (campo {@code isActive=false}), por lo que
 * todas las consultas filtran explícitamente {@code isActive = true} para no mostrar
 * productos eliminados.
 */
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Lista los productos activos, ordenados alfabéticamente.
     *
     * <p>{@code tiendaId} nulo indica SUPER_ADMIN viendo todas las tiendas (no se filtra por tienda);
     * cualquier otro valor restringe el resultado a esa tienda.
     */
    @Query("SELECT p FROM Product p WHERE p.isActive = true " +
           "AND (:tiendaId IS NULL OR p.tienda.id = :tiendaId) ORDER BY p.name ASC")
    List<Product> findAllActive(@Param("tiendaId") Long tiendaId);

    /**
     * Búsqueda paginada de productos activos con filtros combinables.
     *
     * <p>{@code q} busca coincidencia parcial (insensible a mayúsculas) en nombre o código de
     * barras; el {@code CAST(:q AS string)} evita un bug de Postgres al inferir el tipo de un
     * parámetro de texto nulo. {@code categoryId} y {@code lowStock} son opcionales (nulo = sin
     * filtrar); {@code lowStock = true} limita el resultado a productos cuyo stock ya llegó o
     * bajó del mínimo configurado. {@code tiendaId} nulo indica SUPER_ADMIN viendo todas las
     * tiendas; cualquier otro valor restringe el resultado a esa tienda.
     */
    @Query("SELECT p FROM Product p WHERE p.isActive = true " +
           "AND (:q IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%',CAST(:q AS string),'%')) OR LOWER(p.barcode) LIKE LOWER(CONCAT('%',CAST(:q AS string),'%'))) " +
           "AND (:categoryId IS NULL OR p.category.id = :categoryId) " +
           "AND (:lowStock IS NULL OR (:lowStock = true AND p.minStock > 0 AND p.stock <= p.minStock)) " +
           "AND (:tiendaId IS NULL OR p.tienda.id = :tiendaId)")
    Page<Product> searchActive(@Param("q") String q,
                                @Param("categoryId") Long categoryId,
                                @Param("lowStock") Boolean lowStock,
                                @Param("tiendaId") Long tiendaId,
                                Pageable pageable);

    /**
     * Lista los productos activos con stock bajo de una tienda (o de todas si {@code tiendaId}
     * es null, uso de SUPER_ADMIN), ordenados de menor a mayor stock. Exige {@code minStock > 0}:
     * un platillo de restaurante normalmente no lleva inventario, así que uno con stock/mínimo
     * en 0 (nunca configurados) no debe aparecer como "stock bajo".
     */
    @Query("SELECT p FROM Product p WHERE p.isActive = true AND p.minStock > 0 AND p.stock <= p.minStock " +
           "AND (:tiendaId IS NULL OR p.tienda.id = :tiendaId) ORDER BY p.stock ASC")
    List<Product> findLowStockForTienda(@Param("tiendaId") Long tiendaId);

    /**
     * Productos disponibles ordenados por cuánto se han pedido (más vendidos primero), para
     * el buscador rápido de la toma de órdenes. Un producto sin historial de ventas igual
     * aparece (LEFT JOIN), solo que al final, ordenado alfabéticamente como desempate.
     * {@code categoryId} nulo no filtra por categoría (ranking general).
     */
    @Query(value = "SELECT p.* FROM products p " +
                    "LEFT JOIN (SELECT producto_id, SUM(cantidad) AS total_vendido FROM orden_detalles " +
                    "           WHERE estado <> 'CANCELADO' GROUP BY producto_id) v ON v.producto_id = p.id " +
                    "WHERE p.is_active = true AND p.disponible = true " +
                    "AND (:categoryId IS NULL OR p.category_id = :categoryId) " +
                    "AND (:tiendaId IS NULL OR p.tienda_id = :tiendaId) " +
                    "ORDER BY COALESCE(v.total_vendido, 0) DESC, p.name ASC LIMIT :limit", nativeQuery = true)
    List<Product> topSelling(@Param("categoryId") Long categoryId, @Param("tiendaId") Long tiendaId, @Param("limit") int limit);

    /**
     * Busca un producto activo por su código de barras EXACTO, dentro de una tienda.
     *
     * <p>A diferencia de {@link #searchActive} (coincidencia parcial, pensada para que un
     * humano teclee), esta consulta es para los flujos de lector de código de barras: el
     * escáner manda el código completo de una sola vez, y una coincidencia parcial podría
     * ser ambigua si un código es substring de otro. {@code tiendaId} nulo indica
     * SUPER_ADMIN (sin filtrar); cualquier otro valor restringe la búsqueda a esa tienda.</p>
     */
    @Query("SELECT p FROM Product p WHERE p.isActive = true AND p.barcode = :barcode " +
           "AND (:tiendaId IS NULL OR p.tienda.id = :tiendaId)")
    Optional<Product> findByBarcodeExact(@Param("barcode") String barcode, @Param("tiendaId") Long tiendaId);

    /**
     * Busca productos activos CON STOCK por nombre/código, pero en un CONJUNTO de tiendas
     * en vez de una sola — usado por {@code ProductService#searchSiblingStock} para que
     * cualquier rol (no solo SUPER_ADMIN/SUPERVISOR) pueda ver si una sucursal hermana
     * (mismo SUPERVISOR, ver {@code TiendaRepository#findSiblingTiendas}) tiene existencias
     * de un producto, sin poder ver ni tocar nada más de esas tiendas. {@code q} es
     * obligatorio a propósito (a diferencia de {@link #searchActive}): sin texto, listar el
     * catálogo completo de varias tiendas ajenas no tendría sentido para este caso de uso.
     */
    @Query("SELECT p FROM Product p WHERE p.isActive = true AND p.stock > 0 AND p.tienda.id IN :tiendaIds " +
           "AND (LOWER(p.name) LIKE LOWER(CONCAT('%',CAST(:q AS string),'%')) OR LOWER(p.barcode) LIKE LOWER(CONCAT('%',CAST(:q AS string),'%'))) " +
           "ORDER BY p.tienda.name ASC, p.name ASC")
    Page<Product> searchAcrossTiendas(@Param("tiendaIds") List<Long> tiendaIds,
                                       @Param("q") String q,
                                       Pageable pageable);
}
