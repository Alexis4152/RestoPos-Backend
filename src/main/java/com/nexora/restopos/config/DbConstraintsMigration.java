package com.nexora.restopos.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Reglas de unicidad que ddl-auto=update no sabe crear (índices únicos PARCIALES) y que
 * protegen contra operaciones simultáneas aunque falle cualquier validación en el código:
 * <ul>
 *   <li>un usuario no puede tener dos cortes de caja abiertos a la vez;</li>
 *   <li>una mesa no puede tener dos órdenes activas (ni pagadas ni canceladas) a la vez.</li>
 * </ul>
 * Además, los índices de las consultas más frecuentes.
 * Idempotente ({@code IF NOT EXISTS}): corre en cada arranque. Si ya hubiera datos que
 * violan la regla, solo registra una advertencia y la app sigue arrancando.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DbConstraintsMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        crear("ux_cash_cuts_un_abierto_por_usuario",
                "CREATE UNIQUE INDEX IF NOT EXISTS ux_cash_cuts_un_abierto_por_usuario ON cash_cuts (user_id) WHERE status = 'OPEN'");
        crear("ux_ordenes_una_activa_por_mesa",
                "CREATE UNIQUE INDEX IF NOT EXISTS ux_ordenes_una_activa_por_mesa ON ordenes (mesa_id) " +
                "WHERE mesa_id IS NOT NULL AND estado NOT IN ('PAGADA', 'CANCELADA')");

        // Índices para las consultas frecuentes (Cocina, Para llevar, Entregas, avisos, historial
        // del cliente). Los parciales solo indexan las filas "vivas", así se quedan chicos.
        crear("ix_ordenes_tienda_estado", "CREATE INDEX IF NOT EXISTS ix_ordenes_tienda_estado ON ordenes (tienda_id, estado)");
        crear("ix_ordenes_cliente_fecha", "CREATE INDEX IF NOT EXISTS ix_ordenes_cliente_fecha ON ordenes (cliente_id, opened_at DESC) WHERE cliente_id IS NOT NULL");
        crear("ix_ordenes_por_aceptar", "CREATE INDEX IF NOT EXISTS ix_ordenes_por_aceptar ON ordenes (tienda_id) WHERE pendiente_aceptar = true");
        crear("ix_ordenes_envios_pendientes", "CREATE INDEX IF NOT EXISTS ix_ordenes_envios_pendientes ON ordenes (tienda_id, opened_at) WHERE tipo_entrega = 'DOMICILIO' AND entregada_at IS NULL");
        crear("ix_ordenes_mesero_activas", "CREATE INDEX IF NOT EXISTS ix_ordenes_mesero_activas ON ordenes (mesero_id) WHERE estado NOT IN ('PAGADA', 'CANCELADA')");
        crear("ix_orden_detalles_entregado_cocina", "CREATE INDEX IF NOT EXISTS ix_orden_detalles_entregado_cocina ON orden_detalles (entregado_cocina_at) WHERE entregado_cocina_at IS NOT NULL");
        crear("ix_orden_detalle_modificadores_detalle", "CREATE INDEX IF NOT EXISTS ix_orden_detalle_modificadores_detalle ON orden_detalle_modificadores (orden_detalle_id)");
        crear("ix_comensales_orden", "CREATE INDEX IF NOT EXISTS ix_comensales_orden ON comensales (orden_id)");
        crear("ix_cliente_sesiones_cliente", "CREATE INDEX IF NOT EXISTS ix_cliente_sesiones_cliente ON cliente_sesiones (cliente_id)");
        crear("ix_cliente_sesiones_vence", "CREATE INDEX IF NOT EXISTS ix_cliente_sesiones_vence ON cliente_sesiones (expires_at)");
    }

    private void crear(String nombre, String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception e) {
            log.warn("No se pudo crear el índice {} (¿datos duplicados existentes?): {}", nombre, e.getMessage());
        }
    }
}
