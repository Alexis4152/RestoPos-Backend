package com.nexora.restopos.repository;

import com.nexora.restopos.model.ModificadorGrupo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Repositorio de {@link ModificadorGrupo}. El borrado es siempre suave ({@code isActive=false}). */
public interface ModificadorGrupoRepository extends JpaRepository<ModificadorGrupo, Long> {
    List<ModificadorGrupo> findAllByProductoIdAndIsActiveTrueOrderByOrdenAscIdAsc(Long productoId);

    /**
     * Grupos activos de OTROS productos de la misma categoría — para poder copiarlos a este
     * producto en vez de recapturar desde cero uno ya armado para un platillo similar (ver
     * ModificadorGrupoService#findCopiablesDeLaMismaCategoria). Como la categoría en sí ya está
     * acotada a una tienda, cualquier producto que la referencie pertenece a esa misma tienda —
     * no hace falta filtrar tienda aparte.
     */
    List<ModificadorGrupo> findAllByProducto_Category_IdAndIsActiveTrueAndProducto_IdNotOrderByNombreAsc(
            Long categoryId, Long productoId);
}
