-- =====================================================================
--  Migración: envío gratis y seguimiento del repartidor configurable
--
--  - tiendas.seguimiento_repartidor (default true): los pedidos a domicilio del punto de
--    venta van a Entregas con seguimiento del repartidor. En false (repartidores externos)
--    se cobran en caja y se entregan como para llevar. Solo lo cambia el SUPER_ADMIN.
--  - ordenes.sin_seguimiento (default false): con qué regla nació cada pedido.
--
--  El envío gratis no necesita columnas: es la línea de envío en $0 ("Envío a domicilio GRATIS").
--  PostgreSQL. UNA SOLA SENTENCIA (bloque DO), se puede correr más de una vez.
--  Deshacer: rollback_envio_seguimiento.sql
-- =====================================================================
DO $$
BEGIN
  ALTER TABLE tiendas ADD COLUMN IF NOT EXISTS seguimiento_repartidor boolean;
  UPDATE tiendas SET seguimiento_repartidor = true WHERE seguimiento_repartidor IS NULL;
  ALTER TABLE tiendas ALTER COLUMN seguimiento_repartidor SET DEFAULT true;
  ALTER TABLE tiendas ALTER COLUMN seguimiento_repartidor SET NOT NULL;

  ALTER TABLE ordenes ADD COLUMN IF NOT EXISTS sin_seguimiento boolean;
  UPDATE ordenes SET sin_seguimiento = false WHERE sin_seguimiento IS NULL;
  ALTER TABLE ordenes ALTER COLUMN sin_seguimiento SET DEFAULT false;
  ALTER TABLE ordenes ALTER COLUMN sin_seguimiento SET NOT NULL;

  RAISE NOTICE 'Envío gratis y seguimiento del repartidor listos';
END $$;
