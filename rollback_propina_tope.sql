-- =====================================================================
--  Rollback de migration_propina_tope.sql
--
--  Quita tiendas.propina_max_porcentaje. Antes de correrlo, despliega una versión del
--  backend sin el campo Tienda.propinaMaxPorcentaje: con ddl-auto=update, la versión nueva
--  lo volvería a crear. Se pierde el tope que cada tienda haya configurado.
--
--  PostgreSQL. UNA SOLA SENTENCIA (bloque DO), se puede correr más de una vez.
-- =====================================================================
DO $$
BEGIN
  ALTER TABLE tiendas DROP CONSTRAINT IF EXISTS tiendas_propina_max_porcentaje_check;
  ALTER TABLE tiendas DROP COLUMN IF EXISTS propina_max_porcentaje;
  RAISE NOTICE 'Tope de propina eliminado';
END $$;
