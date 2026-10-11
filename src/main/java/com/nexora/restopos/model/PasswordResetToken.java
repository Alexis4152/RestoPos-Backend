package com.nexora.restopos.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Token de recuperación de contraseña: string opaco (no JWT) que vive 30 minutos, entregado
 * por correo como link ({@code AuthService#forgotPassword}) y consumido una sola vez
 * ({@code AuthService#resetPassword}, que lo marca {@code used=true} al usarlo).
 * <p>
 * Al pedir un link nuevo, todos los tokens sin usar previos del mismo usuario se marcan
 * {@code used=true} (ver {@code PasswordResetTokenRepository#invalidateAllForUser}) — solo el
 * correo más reciente enviado sirve.
 */
@Entity
@Table(name = "password_reset_tokens")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PasswordResetToken {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    @Builder.Default
    private Boolean used = false;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
