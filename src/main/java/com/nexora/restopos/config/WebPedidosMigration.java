package com.nexora.restopos.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Ajuste de esquema que {@code ddl-auto=update} no hace solo: un pedido de la página web llega
 * sin mesero (lo "toma" quien lo acepta, ver OrdenService#aceptarPedidoWeb), así que
 * {@code ordenes.mesero_id} deja de ser NOT NULL. Idempotente: en Postgres, quitar un NOT NULL
 * que ya no existe no falla, así que corre en cada arranque sin problema.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebPedidosMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("ALTER TABLE ordenes ALTER COLUMN mesero_id DROP NOT NULL");
        } catch (Exception e) {
            log.warn("No se pudo quitar NOT NULL de ordenes.mesero_id: {}", e.getMessage());
        }
    }
}
