-- =====================================================================
--  Migración: corte de caja v2 (traído de DemoPV)
--
--  - cash_movements.destination_other: destino escrito a mano en un retiro con destino "Otro".
--  - cash_movements_destination_check: ahora acepta OTHER.
--  - cash_cuts.refunds_total: devoluciones en efectivo hechas en el corte (pagos negativos).
--
--  Los cortes existentes quedan con refunds_total NULL (se muestran como $0).
--  PostgreSQL. UNA SOLA SENTENCIA (bloque DO), se puede correr más de una vez (si Hibernate
--  ya creó las columnas con ddl-auto=update, solo asegura la restricción).
--  Deshacer: rollback_caja_v2.sql
-- =====================================================================
DO $$
BEGIN
  ALTER TABLE cash_movements ADD COLUMN IF NOT EXISTS destination_other varchar(100);
  ALTER TABLE cash_movements DROP CONSTRAINT IF EXISTS cash_movements_destination_check;
  ALTER TABLE cash_movements ADD CONSTRAINT cash_movements_destination_check
      CHECK (destination IN ('OWNER', 'BANK', 'SAFE', 'OTHER'));
  ALTER TABLE cash_cuts ADD COLUMN IF NOT EXISTS refunds_total numeric(12,2);
  RAISE NOTICE 'Corte de caja v2 listo';
END $$;
