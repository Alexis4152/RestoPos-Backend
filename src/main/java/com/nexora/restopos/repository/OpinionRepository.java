package com.nexora.restopos.repository;

import com.nexora.restopos.model.Opinion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OpinionRepository extends JpaRepository<Opinion, Long> {

    Optional<Opinion> findByCuentaId(Long cuentaId);

    List<Opinion> findByOrdenId(Long ordenId);

    /** Opiniones de mesa de una tienda en un rango de fechas, más recientes primero (pantalla "Opiniones"). */
    @Query("SELECT o FROM Opinion o WHERE (:tiendaId IS NULL OR o.tienda.id = :tiendaId) " +
           "AND o.createdAt BETWEEN :desde AND :hasta ORDER BY o.createdAt DESC")
    List<Opinion> buscar(@Param("tiendaId") Long tiendaId, @Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);
}
