package com.nexora.restopos.repository;

import com.nexora.restopos.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

/**
 * Repositorio de {@link Category}.
 *
 * <p>El borrado de una categoría es siempre suave (campo {@code isActive=false}, sin eliminar
 * la fila físicamente), por lo que ambas consultas de listado filtran explícitamente
 * {@code isActive = true} para no mostrar categorías eliminadas.
 */
public interface CategoryRepository extends JpaRepository<Category, Long> {

    /** Lista todas las categorías activas (no eliminadas), ordenadas alfabéticamente. */
    List<Category> findAllByIsActiveTrueOrderByNameAsc();

    /**
     * Lista las categorías activas de una tienda, ordenadas alfabéticamente.
     *
     * <p>{@code tiendaId} nulo indica SUPER_ADMIN viendo todas las tiendas (no se filtra por tienda);
     * cualquier otro valor restringe el resultado a esa tienda.
     */
    @Query("SELECT c FROM Category c WHERE c.isActive = true AND (:tiendaId IS NULL OR c.tienda.id = :tiendaId) ORDER BY c.name ASC")
    List<Category> findAllForTienda(@Param("tiendaId") Long tiendaId);
}
