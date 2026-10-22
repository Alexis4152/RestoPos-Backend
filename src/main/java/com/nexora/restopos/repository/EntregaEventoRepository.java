package com.nexora.restopos.repository;

import com.nexora.restopos.model.EntregaEvento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EntregaEventoRepository extends JpaRepository<EntregaEvento, Long> {

    List<EntregaEvento> findByOrdenIdOrderByCreatedAtAscIdAsc(Long ordenId);

    boolean existsByOrdenIdAndTipo(Long ordenId, String tipo);

    /** Envíos que un repartidor soltó desde {@code desde}, de la tienda (null = todas). Para la campana. */
    @org.springframework.data.jpa.repository.Query("SELECT e FROM EntregaEvento e, Orden o WHERE o.id = e.ordenId " +
           "AND e.tipo = 'SOLTADO' AND e.createdAt >= :desde AND (:tiendaId IS NULL OR o.tienda.id = :tiendaId) ORDER BY e.createdAt DESC")
    List<EntregaEvento> findSoltadosDesde(@org.springframework.data.repository.query.Param("tiendaId") Long tiendaId,
                                         @org.springframework.data.repository.query.Param("desde") java.time.LocalDateTime desde);
}
