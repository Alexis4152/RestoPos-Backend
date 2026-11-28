-- =====================================================================
--  Migración: favoritos de la tienda
--
--  products.favorito (default false): el platillo aparece en la zona "Favoritos" al tomar la
--  orden (para todos en la tienda). Se marca con la estrella desde la toma de orden.
--
--  PostgreSQL. UNA SOLA SENTENCIA (bloque DO), se puede correr más de una vez.
--  Deshacer: rollback_favoritos.sql
-- =====================================================================
DO $$
BEGIN
  ALTER TABLE products ADD COLUMN IF NOT EXISTS favorito boolean;
  UPDATE products SET favorito = false WHERE favorito IS NULL;
  ALTER TABLE products ALTER COLUMN favorito SET DEFAULT false;
  ALTER TABLE products ALTER COLUMN favorito SET NOT NULL;
  RAISE NOTICE 'Favoritos listos';
END $$;
