-- Deshace migration_favoritos.sql.
DO $$
BEGIN
  ALTER TABLE products DROP COLUMN IF EXISTS favorito;
  RAISE NOTICE 'Favoritos deshechos';
END $$;
