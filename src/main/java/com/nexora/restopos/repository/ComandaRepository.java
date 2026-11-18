package com.nexora.restopos.repository;

import com.nexora.restopos.model.Comanda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/** Cola de {@link Comanda}s por imprimir (ver ComandaService). */
public interface ComandaRepository extends JpaRepository<Comanda, Long> {

    /** Pendientes de una tienda creadas después de {@code desde}, en orden de llegada. */
    List<Comanda> findTop20ByTiendaIdAndTomadaAtIsNullAndCreatedAtAfterOrderByIdAsc(Long tiendaId, LocalDateTime desde);

    /**
     * Toma una comanda para imprimirla, de forma atómica: solo la primera estación que llega la
     * obtiene (si hay dos computadoras con la estación activa, nunca se imprime dos veces).
     *
     * @return 1 si se tomó, 0 si otra estación ya la había tomado
     */
    @Modifying
    @Query("UPDATE Comanda c SET c.tomadaAt = :ahora, c.intentos = c.intentos + 1 WHERE c.id = :id AND c.tomadaAt IS NULL")
    int tomar(@Param("id") Long id, @Param("ahora") LocalDateTime ahora);
}
