-- Deshace migration_caja_v2.sql. Los retiros con destino "Otro" pasan a "Caja fuerte" (SAFE)
-- para poder restaurar la restricción anterior; se pierde el texto escrito a mano.
DO $$
BEGIN
  UPDATE cash_movements SET destination = 'SAFE' WHERE destination = 'OTHER';
  ALTER TABLE cash_movements DROP CONSTRAINT IF EXISTS cash_movements_destination_check;
  ALTER TABLE cash_movements ADD CONSTRAINT cash_movements_destination_check
      CHECK (destination IN ('OWNER', 'BANK', 'SAFE'));
  ALTER TABLE cash_movements DROP COLUMN IF EXISTS destination_other;
  ALTER TABLE cash_cuts DROP COLUMN IF EXISTS refunds_total;
  RAISE NOTICE 'Corte de caja v2 deshecho';
END $$;
