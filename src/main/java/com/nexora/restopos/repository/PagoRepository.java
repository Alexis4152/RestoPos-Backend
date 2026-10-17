package com.nexora.restopos.repository;

import com.nexora.restopos.model.Pago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Repositorio de {@link Pago}: es la fuente de verdad de dinero cobrado, usada por el corte de caja y los reportes. */
public interface PagoRepository extends JpaRepository<Pago, Long> {

    List<Pago> findByCashCutId(Long cashCutId);

    /** Pagos vigentes (no cancelados) de todas las cuentas de una orden, en el orden en que se hicieron. */
    List<Pago> findByCuentaOrdenIdAndCanceladoFalseOrderByCreatedAtAsc(Long ordenId);

    @Query("SELECT COALESCE(SUM(p.monto),0) FROM Pago p WHERE p.cancelado = false AND p.createdAt BETWEEN :from AND :to " +
           "AND (:tiendaId IS NULL OR p.cuenta.orden.tienda.id = :tiendaId)")
    BigDecimal totalBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, @Param("tiendaId") Long tiendaId);

    @Query("SELECT COUNT(p) FROM Pago p WHERE p.cancelado = false AND p.createdAt BETWEEN :from AND :to " +
           "AND (:tiendaId IS NULL OR p.cuenta.orden.tienda.id = :tiendaId)")
    long countBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, @Param("tiendaId") Long tiendaId);

    @Query(value = "SELECT CAST(p.created_at AS date) AS dia, SUM(p.monto) AS total " +
                    "FROM pagos p JOIN cuentas c ON c.id = p.cuenta_id JOIN ordenes o ON o.id = c.orden_id " +
                    "WHERE p.cancelado = false AND p.created_at BETWEEN :from AND :to " +
                    "AND (:tiendaId IS NULL OR o.tienda_id = :tiendaId) " +
                    "GROUP BY dia ORDER BY dia", nativeQuery = true)
    List<Object[]> salesByDay(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, @Param("tiendaId") Long tiendaId);

    /**
     * Totales de los pagos de un corte agrupados por método y si se cancelaron: filas
     * {@code [PaymentMethod, Boolean cancelado, cantidad, suma]}. Lo usa el cálculo del
     * efectivo esperado ({@code CashBalanceCalculator}): una sola consulta agregada.
     */
    @Query("SELECT p.metodo, p.cancelado, COUNT(p), COALESCE(SUM(p.monto), 0) FROM Pago p " +
           "WHERE p.cashCut.id = :cutId GROUP BY p.metodo, p.cancelado")
    List<Object[]> sumByCashCut(@Param("cutId") Long cutId);
}
