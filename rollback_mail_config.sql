-- =====================================================================
--  Rollback de migration_mail_config.sql
--
--  Borra la tabla mail_config. Antes de correrlo, despliega una versión del backend sin
--  MailConfig: con ddl-auto=update, la versión nueva la volvería a crear. El envío de correo
--  vuelve a depender solo de APP_MAIL_USERNAME/APP_MAIL_PASSWORD.
--
--  PostgreSQL. UNA SOLA SENTENCIA (bloque DO), se puede correr más de una vez.
-- =====================================================================
DO $$
BEGIN
  DROP TABLE IF EXISTS mail_config;
  RAISE NOTICE 'Tabla mail_config eliminada';
END $$;
