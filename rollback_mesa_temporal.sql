-- =====================================================================
--  Rollback de migration_mesa_temporal.sql
--
--  Quita mesas.temporal. Antes de correrlo, despliega una versión del backend sin el campo
--  Mesa.temporal: con ddl-auto=update, la versión nueva lo volvería a crear. Las mesas
--  temporales que sigan abiertas quedarían como mesas normales: ciérralas antes.
--
--  PostgreSQL. UNA SOLA SENTENCIA (bloque DO), se puede correr más de una vez.
-- =====================================================================
DO $$
BEGIN
  ALTER TABLE mesas DROP COLUMN IF EXISTS temporal;
  RAISE NOTICE 'Mesas temporales eliminadas';
END $$;
