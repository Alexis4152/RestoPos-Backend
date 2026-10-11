package com.nexora.restopos.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Refresh token de sesión: string opaco (no JWT) que vive {@code app.jwt.refresh-expiration}
 * (8h) y se entrega al frontend como cookie httpOnly (nunca visible a JS, ver
 * {@code AuthController#login}). A diferencia del access token JWT, este SÍ es revocable de
 * verdad porque cada uso se valida contra esta tabla ({@code revoked}/{@code expiresAt}), no
 * solo contra una firma.
 * <p>
 * Reusable dentro de su ventana de vigencia (no rota en cada refresh) — así un usuario puede
 * tener varias pestañas/peticiones en paralelo usando el mismo refresh token sin invalidarse
 * entre sí. Se revoca puntualmente en logout ({@code AuthService#logout}) o en bloque para
 * todas las sesiones de un usuario al cambiar contraseña ({@code AuthService#changePassword}).
 */
@Entity
@Table(name = "refresh_tokens")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class RefreshToken {

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
    private Boolean revoked = false;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
