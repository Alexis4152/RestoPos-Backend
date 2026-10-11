package com.nexora.restopos.repository;

import com.nexora.restopos.model.PasswordResetToken;
import com.nexora.restopos.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/** Repositorio de {@link PasswordResetToken}. */
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    /** Busca un token de reset por su valor (el que viaja en el link del correo). */
    Optional<PasswordResetToken> findByToken(String token);

    // Se invoca al pedir un link nuevo (AuthService#forgotPassword): solo el correo más
    // reciente enviado debe servir, así que los anteriores sin usar quedan invalidados.
    /** Marca como usados todos los tokens de reset sin usar de un usuario. */
    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.used = true WHERE t.user = :user AND t.used = false")
    void invalidateAllForUser(@Param("user") User user);
}
