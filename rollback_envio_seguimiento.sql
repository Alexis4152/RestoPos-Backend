-- Deshace migration_envio_seguimiento.sql. Los pedidos que nacieron sin seguimiento vuelven a
-- tratarse como domicilio con repartidor. Las líneas "Envío a domicilio GRATIS" se quedan en $0.
DO $$
BEGIN
  ALTER TABLE ordenes DROP COLUMN IF EXISTS sin_seguimiento;
  ALTER TABLE tiendas DROP COLUMN IF EXISTS seguimiento_repartidor;
  RAISE NOTICE 'Envío/seguimiento deshecho';
END $$;
