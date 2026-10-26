-- Rollback de migration_reservas_avisos.sql. PostgreSQL, UNA SOLA SENTENCIA (bloque DO).
DO $$
BEGIN
  ALTER TABLE reservaciones DROP COLUMN IF EXISTS ultimo_cambio;
  RAISE NOTICE 'reservaciones.ultimo_cambio quitada';
END $$;
