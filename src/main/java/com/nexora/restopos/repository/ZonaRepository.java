package com.nexora.restopos.repository;

import com.nexora.restopos.model.Zona;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Repositorio de {@link Zona}.
 *
 * <p>El borrado de una zona es siempre suave (campo {@code isActive=false}, sin eliminar la
 * fila físicamente), por lo que el listado filtra explícitamente {@code isActive = true} para
 * no mostrar zonas eliminadas.
 */
public interface ZonaRepository extends JpaRepository<Zona, Long> {

    /**
     * Lista las zonas activas de una tienda, ordenadas alfabéticamente.
     *
     * <p>{@code tiendaId} nulo indica SUPER_ADMIN viendo todas las tiendas (no se filtra por
     * tienda); cualquier otro valor restringe el resultado a esa tienda.
     */
    @Query("SELECT z FROM Zona z WHERE z.isActive = true AND (:tiendaId IS NULL OR z.tienda.id = :tiendaId) ORDER BY z.nombre ASC")
    List<Zona> findAllForTienda(@Param("tiendaId") Long tiendaId);

    /** Primera zona activa (por nombre) de una tienda, o vacío si no tiene ninguna — usado para el backfill de mesas sin zona. */
    java.util.Optional<Zona> findFirstByTiendaIdAndIsActiveTrueOrderByNombreAsc(Long tiendaId);
}
