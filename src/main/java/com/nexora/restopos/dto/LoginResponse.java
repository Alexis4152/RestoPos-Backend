package com.nexora.restopos.dto;

import com.nexora.restopos.model.Tienda;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Respuesta devuelta por {@code POST /api/auth/login} tras una autenticación exitosa.
 * Incluye el JWT que el frontend debe enviar en las siguientes peticiones, los datos
 * básicos del usuario autenticado y la información necesaria para pintar la UI según su
 * rol: las secciones ({@link com.nexora.restopos.model.AppSection}) a las que tiene acceso
 * y, si aplica, la tienda a la que pertenece (para tema/branding).
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class LoginResponse {
    private String token;
    private Long   userId;
    private String name;
    private String email;
    private String role;
    // Nombres de las AppSection a las que el usuario tiene acceso, resueltas a partir de su Role.
    private List<String> sections;
    private Tienda tienda; // null si es SUPER_ADMIN — el frontend usa tienda.primaryColor para el tema
    // true si un admin dio de alta a este usuario (o le reseteó la contraseña) con una
    // temporal generada por el sistema — el frontend debe forzar la pantalla de "cambia tu
    // contraseña" (ver PrivateRoute) antes de dejarlo usar el resto de la app.
    private Boolean mustChangePassword;
    // Link (slug) de la página web pública de su tienda si está activa, o null. El punto de
    // venta lo usa para mandar al repartidor a la página web, que es donde trabaja.
    private String webSlug;
}
