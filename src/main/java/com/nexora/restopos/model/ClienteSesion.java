package com.nexora.restopos.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Sesión de un {@link Cliente} en la página web: el navegador guarda un código aleatorio y
 * aquí solo se guarda su hash SHA-256 (si alguien leyera la tabla, no podría usar las
 * sesiones). Se manda en el header {@code X-Cliente-Token}, distinto del JWT del personal.
 */
@Entity
@Table(name = "cliente_sesiones")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ClienteSesion {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @Column(nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
