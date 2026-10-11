package com.nexora.restopos.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexora.restopos.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Límite de peticiones por IP en los endpoints sin sesión: la página web pública
 * ({@code /api/public/**}: pedidos falsos, fuerza bruta al login de clientes) y el login /
 * recuperación de contraseña del personal (fuerza bruta de credenciales, spam de correos).
 * Ventana fija en memoria — suficiente para un solo servidor; con varias instancias habría
 * que moverlo a Redis.
 * <p>
 * La IP es {@code request.getRemoteAddr()}: detrás de un proxy (nginx/Coolify) hay que activar
 * {@code server.forward-headers-strategy=native} para que sea la del cliente real y no la del
 * proxy (si no, todos los clientes compartirían el mismo límite).
 */
@Component
@RequiredArgsConstructor
public class PublicRateLimitInterceptor implements HandlerInterceptor {

    private record Regla(String clave, int max, long ventanaMs) {}
    private record Ventana(long inicio, int cuenta) {}

    private static final long MINUTO = 60_000;

    private final ConcurrentMap<String, Ventana> ventanas = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    /** Regla que aplica a la petición, o null si no tiene límite. */
    private static Regla reglaPara(HttpServletRequest request) {
        String uri = request.getRequestURI();
        boolean post = "POST".equalsIgnoreCase(request.getMethod());
        if (post && uri.endsWith("/api/auth/login")) return new Regla("login", 10, MINUTO);
        if (post && uri.endsWith("/api/auth/forgot-password")) return new Regla("forgot", 3, 15 * MINUTO);
        // Validar / usar el token del link de recuperación: tope para que no se puedan probar tokens.
        if (uri.startsWith("/api/auth/reset-password")) return new Regla("reset", 20, MINUTO);
        if (uri.startsWith("/api/public/")) {
            if (post && uri.endsWith("/clientes/login")) return new Regla("cliente-login", 10, MINUTO);
            // Mandan correos: límite bajo para que nadie los use para inundar buzones ajenos.
            if (post && (uri.endsWith("/clientes/olvide") || uri.endsWith("/reenviar-verificacion"))) {
                return new Regla("cliente-correo", 3, 15 * MINUTO);
            }
            // Crear pedidos, registrarse, reseñar, reportar: pocos por minuto bastan a un cliente real.
            if (post) return new Regla("public-post", 10, MINUTO);
            // Consultas (menú, mesas, seguimiento — este último se consulta solo cada pocos segundos).
            return new Regla("public-get", 120, MINUTO);
        }
        return null;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Regla regla = reglaPara(request);
        if (regla == null) return true;
        String key = regla.clave() + "|" + request.getRemoteAddr();
        long now = System.currentTimeMillis();
        Ventana w = ventanas.compute(key, (k, cur) ->
                cur == null || now - cur.inicio() >= regla.ventanaMs() ? new Ventana(now, 1) : new Ventana(cur.inicio(), cur.cuenta() + 1));
        if (ventanas.size() > 50_000) ventanas.entrySet().removeIf(e -> now - e.getValue().inicio() >= 15 * MINUTO);
        if (w.cuenta() <= regla.max()) return true;

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResponse.error("Demasiados intentos. Espera un momento e inténtalo de nuevo.")));
        return false;
    }
}
