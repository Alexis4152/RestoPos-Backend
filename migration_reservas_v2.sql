-- =====================================================================
--  Migración: reservaciones v2
--
--  * mesas.reservable (boolean, default true): el admin elige qué mesas se pueden reservar.
--  * tienda_info:
--      reserva_todas_mesas      (boolean, default true)  "todas" o "solo las que marque"
--      reserva_cambios_horas    (integer, default 2)     hasta cuántas horas antes el cliente
--                                                        puede cambiar personas / necesidades
--      reserva_retraso_enabled  (boolean, default true)  "llegaré un poco más tarde"
--      reserva_retraso_horas    (integer, default 3)     hasta cuántas horas antes se puede avisar
--  * reservaciones:
--      retraso_minutos (integer)    minutos de retraso que avisó el cliente
--      modificada_at   (timestamp)  último cambio del cliente
--      preorden_id     (bigint FK ordenes) pedido anticipado (entra a cocina al llegar)
--
--  Todo lo existente queda igual que antes (todas las mesas reservables).
--  PostgreSQL. UNA SOLA SENTENCIA (bloque DO), se puede correr más de una vez.
--  Deshacer: rollback_reservas_v2.sql
-- =====================================================================
DO $$
BEGIN
  ALTER TABLE mesas ADD COLUMN IF NOT EXISTS reservable boolean NOT NULL DEFAULT true;

  ALTER TABLE tienda_info ADD COLUMN IF NOT EXISTS reserva_todas_mesas boolean NOT NULL DEFAULT true;
  ALTER TABLE tienda_info ADD COLUMN IF NOT EXISTS reserva_cambios_horas integer NOT NULL DEFAULT 2;
  ALTER TABLE tienda_info ADD COLUMN IF NOT EXISTS reserva_retraso_enabled boolean NOT NULL DEFAULT true;
  ALTER TABLE tienda_info ADD COLUMN IF NOT EXISTS reserva_retraso_horas integer NOT NULL DEFAULT 3;

  ALTER TABLE reservaciones ADD COLUMN IF NOT EXISTS retraso_minutos integer;
  ALTER TABLE reservaciones ADD COLUMN IF NOT EXISTS modificada_at timestamp;
  ALTER TABLE reservaciones ADD COLUMN IF NOT EXISTS preorden_id bigint;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_reservaciones_preorden') THEN
    ALTER TABLE reservaciones ADD CONSTRAINT fk_reservaciones_preorden FOREIGN KEY (preorden_id) REFERENCES ordenes(id);
  END IF;
  RAISE NOTICE 'Reservaciones v2 listas';
END $$;
