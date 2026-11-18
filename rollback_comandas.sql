-- =====================================================================
--  Rollback de migration_comandas.sql
--
--  Borra la tabla comandas y quita tiendas.enviar_a_cocina. Antes de correrlo, despliega una
--  versión del backend sin Comanda ni Tienda.enviarACocina: con ddl-auto=update, la versión
--  nueva los volvería a crear. Se pierden las comandas pendientes de imprimir.
--
--  PostgreSQL. UNA SOLA SENTENCIA (bloque DO), se puede correr más de una vez.
-- =====================================================================
DO $$
BEGIN
  DROP TABLE IF EXISTS comandas;
  ALTER TABLE tiendas DROP COLUMN IF EXISTS enviar_a_cocina;
  RAISE NOTICE 'Envío a cocina y cola de comandas eliminados';
END $$;
