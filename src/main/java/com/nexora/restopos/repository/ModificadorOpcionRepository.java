package com.nexora.restopos.repository;

import com.nexora.restopos.model.ModificadorOpcion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Repositorio de {@link ModificadorOpcion}. El borrado es siempre suave ({@code isActive=false}). */
public interface ModificadorOpcionRepository extends JpaRepository<ModificadorOpcion, Long> {
    List<ModificadorOpcion> findAllByGrupoIdAndIsActiveTrue(Long grupoId);
}
