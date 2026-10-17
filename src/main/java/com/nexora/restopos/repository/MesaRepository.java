package com.nexora.restopos.repository;

import com.nexora.restopos.model.Mesa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Repositorio de {@link Mesa}. El borrado es siempre suave ({@code isActive=false}). */
public interface MesaRepository extends JpaRepository<Mesa, Long> {

    /**
     * Lista las mesas activas de una tienda, ordenadas por número.
     *
     * <p>{@code tiendaId} nulo indica SUPER_ADMIN viendo todas las tiendas.
     */
    @Query("SELECT m FROM Mesa m WHERE m.isActive = true AND (:tiendaId IS NULL OR m.tienda.id = :tiendaId) ORDER BY m.numero ASC")
    List<Mesa> findAllForTienda(@Param("tiendaId") Long tiendaId);

    boolean existsByNumeroAndTiendaIdAndIsActiveTrue(String numero, Long tiendaId);

    /** Todas las mesas ocupadas por una orden: la principal y las que se le unieron. */
    List<Mesa> findByOrdenActivaId(Long ordenId);

    /** Usado por {@code ZonaService#delete} para impedir borrar una zona que todavía tiene mesas activas. */
    boolean existsByZonaIdAndIsActiveTrue(Long zonaId);
}
