package com.nexora.restopos.repository;

import com.nexora.restopos.model.RefreshToken;
import com.nexora.restopos.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/** Repositorio de {@link RefreshToken}. */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    /** Busca un refresh token por su valor (el que viaja en la cookie httpOnly). */
    Optional<RefreshToken> findByToken(String token);

    // Se invoca al cambiar contraseña (AuthService#changePassword) para cerrar sesión en
    // todos los dispositivos — no borra los tokens, solo los marca revocados, por si se
    // quisiera auditar cuántas sesiones había activas.
    /** Marca como revocados todos los refresh tokens sin revocar de un usuario. */
    @Modifying
    @Query("UPDATE RefreshToken t SET t.revoked = true WHERE t.user = :user AND t.revoked = false")
    void revokeAllForUser(@Param("user") User user);
}
