-- =====================================================================
--  Migración: plan contratado del SUPERVISOR (máximo de tiendas)
--
--  Columna nueva: users.max_tiendas (entero, NULL = sin límite)
--
--  Solo aplica a usuarios con rol SUPERVISOR: el SUPER_ADMIN la fija al registrarlo o
--  editarlo (Usuarios → rol Supervisor → "Máximo de tiendas"). Cuando el supervisor llega
--  a ese número de tiendas activas ya no puede dar de alta más. Los supervisores que ya
--  existen quedan sin límite (NULL), igual que hasta ahora.
--
--  PostgreSQL. UNA SOLA SENTENCIA (bloque DO), se puede correr más de una vez (si Hibernate
--  ya creó la columna con ddl-auto=update, solo se asegura la restricción).
--  Deshacer: rollback_supervisor_plan.sql
-- =====================================================================
DO $$
BEGIN
  ALTER TABLE users ADD COLUMN IF NOT EXISTS max_tiendas integer;

  ALTER TABLE users DROP CONSTRAINT IF EXISTS users_max_tiendas_check;
  ALTER TABLE users ADD CONSTRAINT users_max_tiendas_check
    CHECK (max_tiendas IS NULL OR max_tiendas BETWEEN 0 AND 1000);

  RAISE NOTICE 'Plan de supervisor listo (supervisores existentes sin límite)';
END $$;
