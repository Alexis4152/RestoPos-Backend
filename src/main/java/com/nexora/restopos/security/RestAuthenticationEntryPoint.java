package com.nexora.restopos.security;

import com.nexora.restopos.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Punto de entrada de autenticación de Spring Security: decide qué responde la API cuando
 * un request llega a un endpoint protegido SIN una sesión válida (sin token, token corrupto,
 * mal firmado, o vencido — {@link JwtAuthFilter} no logró poblar el {@code SecurityContext}).
 * <p>
 * Sin este bean, Spring Security cae en su manejador por default, que responde 403 tanto
 * para "no estás autenticado" como para "sí lo estás pero te falta el rol/authority" —
 * ambigüedad que el frontend no puede distinguir. Con este bean, "no autenticado" siempre es
 * 401 (lo que dispara el refresh transparente del interceptor de axios, ver
 * {@code api/axios.js}); un 403 real (autenticado, sin permiso — {@code @PreAuthorize}
 * insuficiente) sigue siendo 403, sin tocar el {@code AccessDeniedHandler} por default.
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                          AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        // Antes de getWriter(): a diferencia de un ResponseEntity normal (que Spring
        // serializa en UTF-8 vía sus HttpMessageConverters), aquí se escribe la respuesta a
        // mano — sin esto, Tomcat cae a ISO-8859-1 por default y cualquier tilde del mensaje
        // sale corrupta.
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                objectMapper.writeValueAsString(ApiResponse.error("Sesión expirada, vuelve a iniciar sesión")));
    }
}
