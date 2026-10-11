package com.nexora.restopos.repository;

import com.nexora.restopos.model.Auditoria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditoriaRepository extends JpaRepository<Auditoria, Long> {

    @Query("SELECT a FROM Auditoria a WHERE (:tiendaId IS NULL OR a.tienda.id = :tiendaId) ORDER BY a.createdAt DESC")
    Page<Auditoria> search(@Param("tiendaId") Long tiendaId, Pageable pageable);
}
