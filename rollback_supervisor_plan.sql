-- =====================================================================
--  Rollback de migration_supervisor_plan.sql
--
--  Quita users.max_tiendas. Antes de correrlo, despliega una versión del backend sin el
--  campo User.maxTiendas: con ddl-auto=update, la versión nueva lo volvería a crear. Se
--  pierde el máximo de tiendas que se haya configurado a cada supervisor.
--
--  PostgreSQL. UNA SOLA SENTENCIA (bloque DO), se puede correr más de una vez.
-- =====================================================================
DO $$
BEGIN
  ALTER TABLE users DROP CONSTRAINT IF EXISTS users_max_tiendas_check;
  ALTER TABLE users DROP COLUMN IF EXISTS max_tiendas;
  RAISE NOTICE 'Plan de supervisor eliminado';
END $$;
