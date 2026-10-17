package com.nexora.restopos.repository;

import com.nexora.restopos.model.Comensal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComensalRepository extends JpaRepository<Comensal, Long> {
    List<Comensal> findByOrdenIdOrderByNumeroAsc(Long ordenId);
    int countByOrdenId(Long ordenId);

    /** Pasa los comensales de una orden a otra, renumerándolos después de los que ya tiene (unir mesas). */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value = "UPDATE comensales SET orden_id = :destino, numero = numero + :desplazar WHERE orden_id = :origen", nativeQuery = true)
    int moverAOrden(@org.springframework.data.repository.query.Param("origen") Long origen,
                    @org.springframework.data.repository.query.Param("destino") Long destino,
                    @org.springframework.data.repository.query.Param("desplazar") int desplazar);
}
