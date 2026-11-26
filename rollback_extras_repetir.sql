-- Deshace migration_extras_repetir.sql. Los extras libres ya guardados quedan como modificadores
-- normales (con su nombre y precio); las opciones repetidas siguen como filas por pieza.
DO $$
BEGIN
  ALTER TABLE modificador_grupos DROP COLUMN IF EXISTS permite_repetir;
  ALTER TABLE orden_detalle_modificadores DROP COLUMN IF EXISTS libre;
  ALTER TABLE tiendas DROP COLUMN IF EXISTS extra_libre;
  RAISE NOTICE 'Extras repetibles y libres deshechos';
END $$;
