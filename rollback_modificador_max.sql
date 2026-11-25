-- Deshace migration_modificador_max.sql (se pierden los máximos configurados).
DO $$
BEGIN
  ALTER TABLE modificador_grupos DROP COLUMN IF EXISTS max_selecciones;
  RAISE NOTICE 'Columna max_selecciones eliminada';
END $$;
