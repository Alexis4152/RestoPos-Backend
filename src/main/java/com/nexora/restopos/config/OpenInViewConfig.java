package com.nexora.restopos.config;

import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * "Open Session in View" para todas las peticiones MENOS las conexiones en vivo (SSE).
 *
 * <p>Con OSIV, la sesión de Hibernate (y su conexión a la base de datos, que Spring retiene
 * hasta el final de la petición) dura lo que dura la petición. Una conexión en vivo dura hasta
 * 30 minutos: cada pantalla de seguimiento abierta en la página web (pedido, reservaciones,
 * "Mis pedidos") se quedaba con una conexión del pool, y con 10 abiertas el pool se agotaba y
 * TODO el backend respondía 500 ("Connection is not available"). Por eso el OSIV automático de
 * Spring Boot está apagado ({@code spring.jpa.open-in-view=false}) y aquí se registra el mismo
 * interceptor, excluyendo esas rutas: las demás pantallas siguen funcionando igual (carga
 * perezosa durante la serialización a JSON, ver JacksonConfig).</p>
 */
@Configuration
@RequiredArgsConstructor
public class OpenInViewConfig implements WebMvcConfigurer {

    /** Rutas de conexiones en vivo (ver LiveEventsController y PublicController). */
    static final String[] RUTAS_EN_VIVO = {
            "/api/events",
            "/api/public/r/*/seguimiento/*/eventos",
            "/api/public/r/*/clientes/me/eventos",
    };

    private final EntityManagerFactory entityManagerFactory;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        OpenEntityManagerInViewInterceptor osiv = new OpenEntityManagerInViewInterceptor();
        osiv.setEntityManagerFactory(entityManagerFactory);
        registry.addWebRequestInterceptor(osiv).excludePathPatterns(RUTAS_EN_VIVO);
    }
}
