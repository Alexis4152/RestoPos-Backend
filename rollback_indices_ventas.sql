-- Deshace migration_indices_ventas.sql (solo borra índices; no toca datos).
DO $$
BEGIN
  DROP INDEX IF EXISTS ix_ordenes_tienda_opened;
  DROP INDEX IF EXISTS ix_cuentas_created_at;
  DROP INDEX IF EXISTS ix_cuentas_orden_created;
  DROP INDEX IF EXISTS ix_modificador_grupos_producto;
  DROP INDEX IF EXISTS ix_modificador_opciones_grupo;
  RAISE NOTICE 'Índices de ventas borrados';
END $$;
