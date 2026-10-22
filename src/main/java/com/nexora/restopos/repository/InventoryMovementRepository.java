package com.nexora.restopos.repository;

import com.nexora.restopos.model.InventoryMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repositorio de {@link InventoryMovement}, la bitácora de entradas/salidas de stock de un producto.
 */
public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {

    /** Lista los movimientos de un producto, del más reciente al más antiguo. */
    List<InventoryMovement> findByProductIdOrderByCreatedAtDesc(Long productId);

    /** Lista todos los movimientos de inventario, del más reciente al más antiguo. */
    Page<InventoryMovement> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Lista los movimientos de un producto dentro de un rango de fechas, del más reciente al más antiguo.
     *
     * <p>{@code from}/{@code to} deben llegar siempre con un valor real (nunca {@code null}), ya que
     * Postgres no puede inferir el tipo de un parámetro {@link LocalDateTime} nulo, por lo que aquí
     * se usa {@code BETWEEN} directo en vez del patrón {@code (:from IS NULL OR ...)}.
     */
    @Query("SELECT m FROM InventoryMovement m WHERE m.product.id = :productId AND m.createdAt BETWEEN :from AND :to ORDER BY m.createdAt DESC")
    List<Object[]> findByProductIdAndCreatedAtBetween(@Param("productId") Long productId,
                                                       @Param("from") LocalDateTime from,
                                                       @Param("to") LocalDateTime to);

    /**
     * Piezas que una línea de orden tiene descontadas en neto (ventas menos devoluciones) —
     * referencia {@code OD:<id>}, ver InventarioVentaService.
     */
    @Query("SELECT COALESCE(SUM(CASE WHEN m.type = com.nexora.restopos.model.MovementType.SALE THEN m.quantity ELSE -m.quantity END), 0) " +
           "FROM InventoryMovement m WHERE m.reference = :referencia")
    int netoVendidoPorReferencia(@Param("referencia") String referencia);
}
