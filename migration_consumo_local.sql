-- =====================================================================
--  Migración: consumo local
--
--  - tiendas.consumo_local_activo (default false) y tiendas.consumo_local_monto (default 10):
--    cargo por pieza a los platillos marcados cuando se consumen en el restaurante.
--  - products.aplica_consumo_local (default false): el platillo cobra consumo local.
--  - orden_detalles.consumo_local (default 0): lo cobrado por pieza en la línea (ya incluido en
--    el subtotal; el ticket del cliente no lo desglosa).
--
--  PostgreSQL. UNA SOLA SENTENCIA (bloque DO), se puede correr más de una vez.
--  Deshacer: rollback_consumo_local.sql
-- =====================================================================
DO $$
BEGIN
  ALTER TABLE tiendas ADD COLUMN IF NOT EXISTS consumo_local_activo boolean;
  UPDATE tiendas SET consumo_local_activo = false WHERE consumo_local_activo IS NULL;
  ALTER TABLE tiendas ALTER COLUMN consumo_local_activo SET DEFAULT false;
  ALTER TABLE tiendas ALTER COLUMN consumo_local_activo SET NOT NULL;

  ALTER TABLE tiendas ADD COLUMN IF NOT EXISTS consumo_local_monto numeric(12,2);
  UPDATE tiendas SET consumo_local_monto = 10 WHERE consumo_local_monto IS NULL;
  ALTER TABLE tiendas ALTER COLUMN consumo_local_monto SET DEFAULT 10;
  ALTER TABLE tiendas ALTER COLUMN consumo_local_monto SET NOT NULL;

  ALTER TABLE products ADD COLUMN IF NOT EXISTS aplica_consumo_local boolean;
  UPDATE products SET aplica_consumo_local = false WHERE aplica_consumo_local IS NULL;
  ALTER TABLE products ALTER COLUMN aplica_consumo_local SET DEFAULT false;
  ALTER TABLE products ALTER COLUMN aplica_consumo_local SET NOT NULL;

  ALTER TABLE orden_detalles ADD COLUMN IF NOT EXISTS consumo_local numeric(12,2);
  UPDATE orden_detalles SET consumo_local = 0 WHERE consumo_local IS NULL;
  ALTER TABLE orden_detalles ALTER COLUMN consumo_local SET DEFAULT 0;
  ALTER TABLE orden_detalles ALTER COLUMN consumo_local SET NOT NULL;

  RAISE NOTICE 'Consumo local listo';
END $$;
