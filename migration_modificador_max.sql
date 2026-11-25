-- =====================================================================
--  Migración: máximo de opciones en grupos de modificadores de selección múltiple
--
--  Columna nueva: modificador_grupos.max_selecciones (integer, NULL = sin límite)
--
--  Solo aplica a grupos MULTIPLE (ej. "Omelet 2 ingredientes" -> máximo 2). Los grupos
--  existentes quedan sin límite (NULL), igual que antes.
--
--  PostgreSQL. UNA SOLA SENTENCIA (bloque DO), se puede correr más de una vez (si Hibernate
--  ya creó la columna con ddl-auto=update, no hace nada).
--  Deshacer: rollback_modificador_max.sql
-- =====================================================================
DO $$
BEGIN
  ALTER TABLE modificador_grupos ADD COLUMN IF NOT EXISTS max_selecciones integer;
  UPDATE modificador_grupos SET max_selecciones = NULL WHERE tipo = 'UNICO' AND max_selecciones IS NOT NULL;
  RAISE NOTICE 'Máximo de opciones en modificadores listo';
END $$;
