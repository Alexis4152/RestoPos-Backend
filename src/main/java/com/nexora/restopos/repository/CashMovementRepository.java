package com.nexora.restopos.repository;

import com.nexora.restopos.model.CashMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CashMovementRepository extends JpaRepository<CashMovement, Long> {

    /** Movimientos registrados dentro de un corte, en orden cronológico. */
    List<CashMovement> findByCashCut_IdOrderByCreatedAtAsc(Long cashCutId);

    /** Suma de los movimientos de un corte por tipo: filas {@code [CashMovementType, suma]}. */
    @Query("SELECT m.type, COALESCE(SUM(m.amount), 0) FROM CashMovement m WHERE m.cashCut.id = :cutId GROUP BY m.type")
    List<Object[]> sumByTypeForCut(@Param("cutId") Long cutId);

    /** Movimientos con la caja cerrada que todavía no recibe nadie (la entrega pendiente). */
    List<CashMovement> findByCashRegisterIdAndCashCutIsNullAndReceivedByCutIsNullOrderByCreatedAtAsc(Long cashRegisterId);

    /** Movimientos con la caja cerrada que vio un corte al recibir la caja. */
    List<CashMovement> findByReceivedByCutIdOrderByCreatedAtAsc(Long cutId);
}
