-- =====================================================================
--  Migración: índices para que Ventas y Órdenes carguen rápido
--
--  - ordenes (tienda_id, opened_at DESC): lista de Órdenes por tienda y rango de fecha.
--  - cuentas (created_at): filtro de fecha de Ventas.
--  - cuentas (orden_id, created_at): fecha y estado de cada venta (sus cuentas).
--  - modificador_grupos (producto_id) y modificador_opciones (grupo_id): cada platillo de una
--    orden trae sus modificadores.
--
--  PostgreSQL. UNA SOLA SENTENCIA (bloque DO), se puede correr más de una vez.
--  Deshacer: rollback_indices_ventas.sql
-- =====================================================================
DO $$
BEGIN
  CREATE INDEX IF NOT EXISTS ix_ordenes_tienda_opened ON ordenes (tienda_id, opened_at DESC);
  CREATE INDEX IF NOT EXISTS ix_cuentas_created_at ON cuentas (created_at);
  CREATE INDEX IF NOT EXISTS ix_cuentas_orden_created ON cuentas (orden_id, created_at);
  CREATE INDEX IF NOT EXISTS ix_modificador_grupos_producto ON modificador_grupos (producto_id);
  CREATE INDEX IF NOT EXISTS ix_modificador_opciones_grupo ON modificador_opciones (grupo_id);
  RAISE NOTICE 'Índices de ventas listos';
END $$;
