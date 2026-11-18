-- =====================================================================
--  Rollback de migration_imprime_cuenta.sql
--
--  Quita tiendas.imprime_cuenta. Antes de correrlo, despliega una versión del backend sin el
--  campo Tienda.imprimeCuenta: con ddl-auto=update, la versión nueva lo volvería a crear. Se
--  pierde la configuración de quién imprime la cuenta en cada tienda.
--
--  PostgreSQL. UNA SOLA SENTENCIA (bloque DO), se puede correr más de una vez.
-- =====================================================================
DO $$
BEGIN
  ALTER TABLE tiendas DROP CONSTRAINT IF EXISTS tiendas_imprime_cuenta_check;
  ALTER TABLE tiendas DROP COLUMN IF EXISTS imprime_cuenta;
  RAISE NOTICE 'Configuración de impresión de cuenta eliminada';
END $$;
