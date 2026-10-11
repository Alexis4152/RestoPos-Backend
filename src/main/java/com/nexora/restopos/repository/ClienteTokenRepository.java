package com.nexora.restopos.repository;

import com.nexora.restopos.model.ClienteToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ClienteTokenRepository extends JpaRepository<ClienteToken, Long> {

    Optional<ClienteToken> findByTokenHashAndTipo(String tokenHash, String tipo);

    /** Invalida los enlaces anteriores del mismo tipo (solo sirve el último que se mandó). */
    @Modifying
    @Query("UPDATE ClienteToken t SET t.usedAt = :now WHERE t.cliente.id = :clienteId AND t.tipo = :tipo AND t.usedAt IS NULL")
    int invalidarPendientes(@Param("clienteId") Long clienteId, @Param("tipo") String tipo, @Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM ClienteToken t WHERE t.expiresAt < :antes")
    int deleteVencidos(@Param("antes") LocalDateTime antes);
}
