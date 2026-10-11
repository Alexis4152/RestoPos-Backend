package com.nexora.restopos.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

/**
 * Componente responsable de generar y validar los JWT usados para autenticar a los usuarios
 * de la API. Usa firma HMAC-SHA256 con un secreto y un tiempo de expiración configurables vía
 * propiedades ({@code app.jwt.secret}, {@code app.jwt.expiration}). El "subject" del token es
 * el email del usuario, que es lo que {@link JwtAuthFilter} usa para recuperar al usuario en
 * cada request.
 */
@Component
public class JwtTokenProvider {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration}")
    private long expiration;

    /**
     * Genera el access token JWT firmado para el usuario dado, con el email como subject,
     * fecha de emisión actual y expiración calculada a partir de {@code app.jwt.expiration}
     * (en milisegundos) — vida corta a propósito (30 min): la renovación la da el refresh
     * token opaco de {@link com.nexora.restopos.model.RefreshToken}, no este JWT.
     *
     * @param userDetails usuario autenticado (su username es el email)
     * @return el JWT compacto y firmado, listo para devolver al cliente
     */
    public String generateAccessToken(UserDetails userDetails) {
        return Jwts.builder()
                .setSubject(userDetails.getUsername())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(signingKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Obtiene el email (subject) codificado en el token.
     *
     * @param token JWT ya validado previamente; si no lo está, este método puede lanzar
     *              excepción al intentar parsearlo
     * @return el email del usuario dueño del token
     */
    public String getEmailFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * Verifica que el token esté correctamente firmado y no haya expirado.
     *
     * @param token JWT a validar
     * @return {@code true} si el token es válido; {@code false} si está corrupto, mal firmado,
     *         expirado, o cualquier otro error de parseo
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Parsea y valida la firma del token, devolviendo sus claims. Lanza {@link JwtException}
     * (o subclases, como expiración) si el token no es válido.
     */
    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /** Construye la clave HMAC de firma a partir del secreto configurado en {@code app.jwt.secret}. */
    private Key signingKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
