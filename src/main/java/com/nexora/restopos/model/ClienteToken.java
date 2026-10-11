package com.nexora.restopos.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Enlace de un solo uso que se manda por correo a un {@link Cliente}: confirmar su correo
 * (VERIFICAR, 48 h) o restablecer su contraseña (RESTABLECER, 1 h). Solo se guarda el hash
 * SHA-256 del código, igual que en {@link ClienteSesion}.
 */
@Entity
@Table(name = "cliente_tokens")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ClienteToken {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime usedAt;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
