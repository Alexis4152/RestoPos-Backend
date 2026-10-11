package com.nexora.restopos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RestoPosApplication {
    public static void main(String[] args) {
        // Todas las fechas del sistema son LocalDateTime "de reloj de pared" (sin zona): "hoy"
        // en Cocina, el plazo de 24 h de los reportes, los cortes automáticos, etc. dependen de
        // la zona de la JVM. En un contenedor esa zona suele ser UTC (6 h adelante de México),
        // así que se fija explícitamente. Se cambia con la variable de entorno APP_TIMEZONE.
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(
                System.getenv().getOrDefault("APP_TIMEZONE", "America/Mexico_City")));
        SpringApplication.run(RestoPosApplication.class, args);
    }
}
