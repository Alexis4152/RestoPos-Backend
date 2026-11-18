-- =====================================================================
--  Migración: mesas temporales
--
--  Columna nueva: mesas.temporal (boolean, default false)
--
--  Cuando todas las mesas están ocupadas, el mesero puede abrir una mesa temporal
--  ("Temporal 1", "Temporal 2"...). Se da de baja sola al liberarse (cobrar, cancelar, mover
--  o separar); la fila se conserva para que en Ventas aparezca como "Mesa Temporal 1".
--  Las mesas existentes quedan como normales (false).
--
--  PostgreSQL. UNA SOLA SENTENCIA (bloque DO), se puede correr más de una vez (si Hibernate
--  ya creó la columna con ddl-auto=update, solo asegura el default).
--  Deshacer: rollback_mesa_temporal.sql
-- =====================================================================
DO $$
BEGIN
  ALTER TABLE mesas ADD COLUMN IF NOT EXISTS temporal boolean;
  UPDATE mesas SET temporal = false WHERE temporal IS NULL;
  ALTER TABLE mesas ALTER COLUMN temporal SET DEFAULT false;
  ALTER TABLE mesas ALTER COLUMN temporal SET NOT NULL;
  RAISE NOTICE 'Mesas temporales listas';
END $$;
