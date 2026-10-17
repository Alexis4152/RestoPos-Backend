package com.nexora.restopos.repository;

import com.nexora.restopos.model.Reimbursement;
import com.nexora.restopos.model.ReimbursementStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface ReimbursementRepository extends JpaRepository<Reimbursement, Long> {

    /** {@code tiendaId} nulo = SUPER_ADMIN sin tienda elegida (no filtra por tienda). */
    @Query("SELECT r FROM Reimbursement r WHERE (:tiendaId IS NULL OR r.tienda.id = :tiendaId) " +
           "AND (:status IS NULL OR r.status = :status) " +
           "ORDER BY r.createdAt DESC")
    Page<Reimbursement> search(@Param("tiendaId") Long tiendaId,
                               @Param("status") ReimbursementStatus status,
                               Pageable pageable);

    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM Reimbursement r " +
           "WHERE (:tiendaId IS NULL OR r.tienda.id = :tiendaId) AND r.status = 'PENDING'")
    BigDecimal sumPending(@Param("tiendaId") Long tiendaId);

    /** Bloqueo pesimista para que un reembolso no se pague dos veces en paralelo. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Reimbursement r WHERE r.id = :id")
    Optional<Reimbursement> findByIdForUpdate(@Param("id") Long id);
}
