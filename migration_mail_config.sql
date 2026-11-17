-- =====================================================================
--  Migración: correo del sistema configurable (tabla mail_config)
--
--  Tabla nueva: mail_config (una sola fila) con la cuenta SMTP con la que salen TODOS los
--  correos (tickets, cortes, contraseñas, clientes de la página web, reservaciones). La
--  administra el SUPER_ADMIN desde Configuración → Correo del sistema.
--
--  La fila NO se inserta aquí: la siembra el backend al arrancar (MailConfigDataInitializer)
--  con la cuenta de APP_MAIL_USERNAME/APP_MAIL_PASSWORD, cifrando la contraseña con
--  APP_ENCRYPTION_KEY. Por eso conviene definir APP_ENCRYPTION_KEY en Coolify ANTES del
--  primer arranque con esta versión.
--
--  PostgreSQL. UNA SOLA SENTENCIA (bloque DO), se puede correr más de una vez (si Hibernate
--  ya creó la tabla con ddl-auto=update, no hace nada).
--  Deshacer: rollback_mail_config.sql
-- =====================================================================
DO $$
BEGIN
  CREATE TABLE IF NOT EXISTS mail_config (
    id                 bigserial PRIMARY KEY,
    enabled            boolean      NOT NULL DEFAULT true,
    smtp_host          varchar(150) NOT NULL,
    smtp_port          integer      NOT NULL,
    smtp_username      varchar(150),
    smtp_password      varchar(255),
    updated_at         timestamp(6),
    updated_by_user_id bigint REFERENCES users(id)
  );
  RAISE NOTICE 'Tabla mail_config lista';
END $$;
