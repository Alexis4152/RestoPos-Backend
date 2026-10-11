package com.nexora.restopos.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuración global que habilita ejecución asíncrona y tareas programadas para toda la
 * aplicación. No expone beans propios: su único propósito es activar las anotaciones
 * {@code @EnableAsync} y {@code @EnableScheduling} a nivel de contexto de Spring.
 */
// @EnableAsync: usado por EmailService para no bloquear la venta mientras se manda el ticket.
// @EnableScheduling: usado por CashCutAutoCloseJob (cierre automático de cortes de caja).
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
}
