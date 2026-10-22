-- =====================================================================
--  Rollback de migration_direccion_partes.sql
--  Quita las columnas calle / colonia / codigo_postal. La dirección completa sigue en
--  "direccion", así que no se pierde ninguna dirección.
--  PostgreSQL. UNA SOLA SENTENCIA (bloque DO).
-- =====================================================================
DO $$
BEGIN
  ALTER TABLE ordenes DROP COLUMN IF EXISTS codigo_postal;
  ALTER TABLE ordenes DROP COLUMN IF EXISTS colonia;
  ALTER TABLE ordenes DROP COLUMN IF EXISTS calle;
  ALTER TABLE clientes DROP COLUMN IF EXISTS codigo_postal;
  ALTER TABLE clientes DROP COLUMN IF EXISTS colonia;
  ALTER TABLE clientes DROP COLUMN IF EXISTS calle;
  RAISE NOTICE 'Dirección en partes deshecha';
END $$;
