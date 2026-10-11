package com.nexora.restopos.config;

import com.nexora.restopos.security.CustomUserDetailsService;
import com.nexora.restopos.security.JwtAuthFilter;
import com.nexora.restopos.security.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Configuración central de Spring Security. Define una API sin sesión (JWT stateless), sin
 * CSRF (no aplica a una API consumida por SPA con tokens), con CORS habilitado para los
 * orígenes de desarrollo del frontend, e inserta {@link JwtAuthFilter} antes del filtro
 * estándar de autenticación por usuario/contraseña para autenticar cada request por su
 * token.
 *
 * <p>{@code @EnableMethodSecurity} habilita las anotaciones {@code @PreAuthorize} usadas en
 * los controllers/services, incluyendo las que consultan {@link
 * com.nexora.restopos.security.SectionAccessService} (bean {@code sectionAccess}) para el RBAC
 * configurable por sección.</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final CustomUserDetailsService userDetailsService;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    /**
     * Cadena de filtros de seguridad HTTP: deshabilita CSRF, activa CORS, fuerza sesiones
     * stateless, permite sin autenticación los endpoints de auth ({@code /api/auth/**}) y los
     * archivos estáticos subidos ({@code /uploads/**}), y exige autenticación para el resto.
     * El filtro JWT se registra antes de {@link UsernamePasswordAuthenticationFilter} para
     * poblar el {@code SecurityContext} a partir del token antes de que Spring intente
     * cualquier otro mecanismo de autenticación.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(restAuthenticationEntryPoint))
                // Encabezados de seguridad. Spring ya manda por defecto X-Content-Type-Options,
                // X-Frame-Options: DENY, Cache-Control no-store y HSTS (solo sobre HTTPS); aquí se
                // suman: la API solo responde JSON/imágenes, así que su CSP puede ser la más
                // estricta (no carga nada ni se puede incrustar), y el Referrer no filtra rutas.
                .headers(h -> h
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .referrerPolicy(r -> r.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
                .authorizeHttpRequests(auth -> auth
                        // SSE (avisos en tiempo real): la conexión ya se autorizó al abrirse; los
                        // despachos asíncronos posteriores no traen el JWT y no deben re-evaluarse.
                        .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ASYNC, jakarta.servlet.DispatcherType.ERROR).permitAll()
                        // Excepciones DENTRO de /api/auth/**, declaradas antes que el permitAll
                        // de abajo (gana la regla más específica que aparezca primero): a
                        // diferencia de login/refresh/logout (anónimos por diseño), cambiar la
                        // propia contraseña y "quién soy" sí exigen sesión — /me en particular
                        // la necesita para que un token inválido/vencido dispare el 401 real de
                        // restAuthenticationEntryPoint (y con él, el refresh transparente del
                        // frontend) en vez de responder 200 con data null vía @AuthenticationPrincipal.
                        .requestMatchers("/api/auth/change-password", "/api/auth/me").authenticated()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/uploads/**").permitAll()
                        // Página web pública de cada tienda (menú, mesas, pedidos): sin sesión
                        // por diseño; el aislamiento entre tiendas lo da el slug de la URL, y el
                        // abuso lo frena PublicRateLimitInterceptor.
                        .requestMatchers("/api/public/**").permitAll()
                        .anyRequest().authenticated()
                )
                .userDetailsService(userDetailsService)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * Encoder de contraseñas usado al dar de alta usuarios y al validar el login. BCrypt
     * genera un salt distinto por hash, por lo que dos contraseñas iguales producen valores
     * almacenados distintos.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Expone el {@link AuthenticationManager} por defecto de Spring Security para que el
     * endpoint de login pueda autenticar credenciales (email/contraseña) manualmente antes de
     * emitir el JWT.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    /**
     * Configura CORS para permitir que el frontend consuma la API con credenciales, incluyendo
     * el header {@code Authorization} usado para enviar el JWT. Los orígenes permitidos vienen
     * de {@code app.cors.allowed-origins} (por default, Vite/CRA local más patrones de red LAN
     * como {@code http://192.168.*.*:5173}, para poder probar desde el celular/otra compu en
     * la misma red); en producción se sobreescribe con la variable de entorno
     * {@code APP_CORS_ALLOWED_ORIGINS} apuntando al dominio real donde quede desplegado el
     * frontend, sin tocar código.
     * <p>
     * Se usa {@code setAllowedOriginPatterns} (no {@code setAllowedOrigins}) porque admite
     * comodines ({@code *}) en cada segmento — necesario para los patrones de LAN de arriba —
     * y sigue siendo compatible con {@code allowCredentials(true)} (a diferencia del comodín
     * "*" de {@code setAllowedOrigins}, que Spring rechaza junto con credenciales).
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(List.of(allowedOrigins.split(",")));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
