package com.nexora.restopos.repository;

import com.nexora.restopos.model.Tienda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Repositorio de {@link Tienda}, la entidad raíz del aislamiento multi-tienda del sistema.
 * El borrado de una tienda es siempre suave (campo {@code isActive=false}).
 */
public interface TiendaRepository extends JpaRepository<Tienda, Long> {
    /** Lista todas las tiendas registradas, ordenadas alfabéticamente. */
    List<Tienda> findAllByOrderByNameAsc();
    /** Lista las tiendas a cargo de un SUPERVISOR específico, ordenadas alfabéticamente. */
    List<Tienda> findBySupervisorIdOrderByNameAsc(Long supervisorId);

    // Todo por subconsulta (nunca se lee tienda.supervisor en Java) para no depender de que
    // esa relación LAZY siga "viva" en este punto del request — ver ProductService#
    // searchSiblingStock, el único caller. NULL nunca calza con NULL en SQL, así que una
    // tienda sin supervisor asignado automáticamente da una lista vacía, no un error.
    /**
     * Tiendas activas "hermanas" de la dada: las que comparten el mismo SUPERVISOR, sin
     * incluir a la propia. Pensado para que cualquier usuario (no solo SUPER_ADMIN/
     * SUPERVISOR) pueda consultar stock disponible en otras sucursales de su mismo grupo,
     * sin poder ver ni tocar nada más de ellas.
     *
     * @param tiendaId id de la tienda de referencia
     * @return tiendas activas con el mismo supervisor, excluyendo la propia; vacía si no
     *         tiene supervisor asignado
     */
    @Query("SELECT t FROM Tienda t WHERE t.isActive = true AND t.id <> :tiendaId " +
           "AND t.supervisor.id = (SELECT t2.supervisor.id FROM Tienda t2 WHERE t2.id = :tiendaId) " +
           "ORDER BY t.name ASC")
    List<Tienda> findSiblingTiendas(@Param("tiendaId") Long tiendaId);
}
