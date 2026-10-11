package com.nexora.restopos.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * Configuración de Spring MVC que publica la carpeta de archivos subidos (actualmente, los
 * logos de tienda) como recursos estáticos accesibles públicamente bajo {@code /uploads/**}
 * (ver también el {@code permitAll()} correspondiente en {@link SecurityConfig}).
 */
// Sirve los archivos subidos (logos de tienda) como recursos estáticos públicos.
// Dev: carpeta local dentro del proyecto backend. Prod: la misma ruta relativa,
// pero apuntando al disco del servidor — solo cambia app.uploads.dir.
@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    @Value("${app.uploads.dir}")
    private String uploadsDir;

    /**
     * Registra {@code /uploads/**} como handler de recursos estáticos apuntando a
     * {@code app.uploads.dir} en disco. Cambiar esa propiedad (por ejemplo, al desplegar a
     * producción) es suficiente para reubicar la carpeta sin tocar código.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = "file:" + Paths.get(uploadsDir).toAbsolutePath() + "/";
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }
}
