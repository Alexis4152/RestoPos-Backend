-- =====================================================================
--  Migración: avisos al personal de lo que hace el cliente con su reservación
--
--  Columna nueva: reservaciones.ultimo_cambio (varchar 150)
--  Texto de la última acción del cliente ("Cambió: de 2 a 4 personas", "Avisó que llega
--  30 min tarde", "Ordenó para su reservación", "Canceló su reservación"...). Con
--  reservaciones.modificada_at alimenta la campana de avisos del personal.
--
--  Requiere migration_reservas_v2.sql (columna modificada_at).
--  PostgreSQL. UNA SOLA SENTENCIA (bloque DO), se puede correr más de una vez.
--  Deshacer: rollback_reservas_avisos.sql
-- =====================================================================
DO $$
BEGIN
  ALTER TABLE reservaciones ADD COLUMN IF NOT EXISTS ultimo_cambio varchar(150);
  RAISE NOTICE 'reservaciones.ultimo_cambio lista';
END $$;
