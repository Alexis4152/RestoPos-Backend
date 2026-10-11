package com.nexora.restopos.repository;

import com.nexora.restopos.model.ClienteSesion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ClienteSesionRepository extends JpaRepository<ClienteSesion, Long> {

    Optional<ClienteSesion> findByTokenHash(String tokenHash);

    void deleteByTokenHash(String tokenHash);

    /** Cierra todas las sesiones de un cliente (al restablecer su contraseña). */
    @Modifying
    @Query("DELETE FROM ClienteSesion s WHERE s.cliente.id = :clienteId")
    int deleteByClienteId(@Param("clienteId") Long clienteId);

    /** Limpieza de sesiones vencidas (se llama de paso al iniciar sesión). */
    @Modifying
    @Query("DELETE FROM ClienteSesion s WHERE s.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}
