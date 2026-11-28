-- Deshace migration_consumo_local.sql. Lo ya cobrado sigue dentro del subtotal de cada línea.
DO $$
BEGIN
  ALTER TABLE tiendas DROP COLUMN IF EXISTS consumo_local_activo;
  ALTER TABLE tiendas DROP COLUMN IF EXISTS consumo_local_monto;
  ALTER TABLE orden_detalles DROP COLUMN IF EXISTS consumo_local;
  RAISE NOTICE 'Consumo local deshecho';
END $$;
