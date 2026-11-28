-- =====================================================================
--  Migración: grupos de Extras con precio editable al vender
--
--  - modificador_grupos.es_extras (default false): al tomar la orden se puede cambiar el precio
--    de una opción o agregar una nueva; se guarda en el grupo del platillo.
--  - tiendas.extra_libre: se retira (el "extra libre" se reemplaza por los grupos de Extras).
--    Las líneas viejas con extra libre se siguen viendo (orden_detalle_modificadores.libre se queda).
--
--  PostgreSQL. UNA SOLA SENTENCIA (bloque DO), se puede correr más de una vez.
--  Deshacer: rollback_grupo_extras.sql
-- =====================================================================
DO $$
BEGIN
  ALTER TABLE modificador_grupos ADD COLUMN IF NOT EXISTS es_extras boolean;
  UPDATE modificador_grupos SET es_extras = false WHERE es_extras IS NULL;
  ALTER TABLE modificador_grupos ALTER COLUMN es_extras SET DEFAULT false;
  ALTER TABLE modificador_grupos ALTER COLUMN es_extras SET NOT NULL;

  ALTER TABLE tiendas DROP COLUMN IF EXISTS extra_libre;

  RAISE NOTICE 'Grupos de Extras listos';
END $$;
