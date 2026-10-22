-- =====================================================================
--  Rollback de migration_reservas_v2.sql
--  Quita las columnas nuevas. Los pedidos anticipados que existan se quedan como órdenes
--  (solo se pierde el enlace con su reservación); cancélalos antes si no los quieres.
--  PostgreSQL. UNA SOLA SENTENCIA (bloque DO).
-- =====================================================================
DO $$
BEGIN
  ALTER TABLE reservaciones DROP CONSTRAINT IF EXISTS fk_reservaciones_preorden;
  ALTER TABLE reservaciones DROP COLUMN IF EXISTS preorden_id;
  ALTER TABLE reservaciones DROP COLUMN IF EXISTS modificada_at;
  ALTER TABLE reservaciones DROP COLUMN IF EXISTS retraso_minutos;

  ALTER TABLE tienda_info DROP COLUMN IF EXISTS reserva_retraso_horas;
  ALTER TABLE tienda_info DROP COLUMN IF EXISTS reserva_retraso_enabled;
  ALTER TABLE tienda_info DROP COLUMN IF EXISTS reserva_cambios_horas;
  ALTER TABLE tienda_info DROP COLUMN IF EXISTS reserva_todas_mesas;

  ALTER TABLE mesas DROP COLUMN IF EXISTS reservable;
  RAISE NOTICE 'Reservaciones v2 deshechas';
END $$;
