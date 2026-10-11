package com.nexora.restopos.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Cliente registrado en la página web pública de una tienda (ver PublicController /
 * ClienteService). Separado a propósito de {@link User}: un cliente no es personal, no tiene
 * rol ni secciones, no aparece en "Usuarios" y su sesión no sirve para la API del personal.
 * El correo es único por tienda (la misma persona puede registrarse en dos restaurantes).
 */
@Entity
@Table(name = "clientes", uniqueConstraints = @UniqueConstraint(columnNames = {"tienda_id", "email"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Cliente {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnoreProperties({"supervisor", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tienda_id", nullable = false)
    private Tienda tienda;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 30)
    private String telefono;

    @Column(nullable = false, length = 200)
    private String direccion;

    // Siempre en minúsculas (ver ClienteService), así la unicidad no depende de mayúsculas.
    @Column(nullable = false, length = 150)
    private String email;

    @JsonIgnore
    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, columnDefinition = "boolean default true")
    @Builder.Default
    private Boolean isActive = true;

    // ¿Confirmó su correo con el enlace que se le mandó al registrarse? Sin confirmar puede
    // entrar y ver el menú, pero no hacer pedidos con su cuenta. "default true" solo para las
    // cuentas que ya existían antes de este cambio; las nuevas nacen en false (ClienteService).
    @Column(nullable = false, columnDefinition = "boolean default true")
    @Builder.Default
    private Boolean emailVerificado = false;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
