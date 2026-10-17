package com.nexora.restopos.repository;

import com.nexora.restopos.model.Shortage;
import com.nexora.restopos.model.ShortageOrigin;
import com.nexora.restopos.model.ShortageStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Optional;

public interface ShortageRepository extends JpaRepository<Shortage, Long> {

    /** {@code tiendaId} nulo = SUPER_ADMIN sin tienda elegida (no filtra por tienda). */
    @Query("SELECT s FROM Shortage s WHERE (:tiendaId IS NULL OR s.tienda.id = :tiendaId) " +
           "AND s.status IN :statuses ORDER BY s.createdAt DESC")
    Page<Shortage> search(@Param("tiendaId") Long tiendaId,
                          @Param("statuses") Collection<ShortageStatus> statuses,
                          Pageable pageable);

    @Query("SELECT COALESCE(SUM(s.amount - s.paidAmount), 0) FROM Shortage s " +
           "WHERE (:tiendaId IS NULL OR s.tienda.id = :tiendaId) AND s.status <> com.nexora.restopos.model.ShortageStatus.PAID")
    BigDecimal sumPending(@Param("tiendaId") Long tiendaId);

    Optional<Shortage> findFirstBySourceCut_IdAndOrigin(Long cutId, ShortageOrigin origin);

    /** Bloqueo pesimista para que dos abonos simultáneos no paguen de más. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Shortage s WHERE s.id = :id")
    Optional<Shortage> findByIdForUpdate(@Param("id") Long id);
}
