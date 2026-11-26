-- =====================================================================
--  Migración: extras repetibles y extras libres
--
--  - modificador_grupos.permite_repetir (default false): en grupos de varias opciones, la misma
--    opción se puede pedir varias veces (Cebolla x3). El máximo cuenta piezas.
--  - orden_detalle_modificadores.libre (default false): extra que no está en el menú, con nombre
--    y precio escritos al tomar la orden.
--  - tiendas.extra_libre (default false): la tienda permite extras libres (solo lo activa el SUPER_ADMIN).
--
--  Una opción repetida se guarda como una fila por pieza: los totales no cambian de cálculo.
--  PostgreSQL. UNA SOLA SENTENCIA (bloque DO), se puede correr más de una vez.
--  Deshacer: rollback_extras_repetir.sql
-- =====================================================================
DO $$
BEGIN
  ALTER TABLE modificador_grupos ADD COLUMN IF NOT EXISTS permite_repetir boolean;
  UPDATE modificador_grupos SET permite_repetir = false WHERE permite_repetir IS NULL;
  ALTER TABLE modificador_grupos ALTER COLUMN permite_repetir SET DEFAULT false;
  ALTER TABLE modificador_grupos ALTER COLUMN permite_repetir SET NOT NULL;

  ALTER TABLE orden_detalle_modificadores ADD COLUMN IF NOT EXISTS libre boolean;
  UPDATE orden_detalle_modificadores SET libre = false WHERE libre IS NULL;
  ALTER TABLE orden_detalle_modificadores ALTER COLUMN libre SET DEFAULT false;
  ALTER TABLE orden_detalle_modificadores ALTER COLUMN libre SET NOT NULL;

  ALTER TABLE tiendas ADD COLUMN IF NOT EXISTS extra_libre boolean;
  UPDATE tiendas SET extra_libre = false WHERE extra_libre IS NULL;
  ALTER TABLE tiendas ALTER COLUMN extra_libre SET DEFAULT false;
  ALTER TABLE tiendas ALTER COLUMN extra_libre SET NOT NULL;

  RAISE NOTICE 'Extras repetibles y libres listos';
END $$;
