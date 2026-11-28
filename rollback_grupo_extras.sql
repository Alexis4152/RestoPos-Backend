-- Deshace migration_grupo_extras.sql (los grupos de Extras vuelven a ser grupos normales).
DO $$
BEGIN
  ALTER TABLE modificador_grupos DROP COLUMN IF EXISTS es_extras;
  ALTER TABLE tiendas ADD COLUMN IF NOT EXISTS extra_libre boolean NOT NULL DEFAULT false;
  RAISE NOTICE 'Grupos de Extras deshechos';
END $$;
