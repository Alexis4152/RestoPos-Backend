package com.nexora.restopos.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtro de Spring Security que autentica cada request a partir del JWT enviado en el header
 * {@code Authorization: Bearer <token>}. Se registra en {@code SecurityConfig} antes de
 * {@code UsernamePasswordAuthenticationFilter}, de modo que corre en cada petición (incluidas
 * las de la API protegida) antes de que Spring intente cualquier otro mecanismo de
 * autenticación. Extiende {@link OncePerRequestFilter} para garantizar una sola ejecución por
 * request incluso si hay forwards/includes internos.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;

    /**
     * Si el request trae un JWT válido, carga el usuario correspondiente y lo coloca en el
     * {@link SecurityContextHolder} como autenticado para el resto de la cadena de filtros y
     * los controllers. Si no hay token o no es válido, simplemente deja pasar el request sin
     * autenticar (será rechazado más adelante por las reglas de autorización si el endpoint
     * lo requiere).
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String token = extractToken(request);

        if (token != null && jwtTokenProvider.validateToken(token)) {
            String email = jwtTokenProvider.getEmailFromToken(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        chain.doFilter(request, response);
    }

    /**
     * Extrae el token del header {@code Authorization}, quitando el prefijo {@code "Bearer "}.
     *
     * @return el JWT sin el prefijo, o {@code null} si el header no está presente o no tiene
     *         el formato esperado
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
