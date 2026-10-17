-- =====================================================================
--  Rollback de migration_cajas_corte.sql
--
--  ⚠️ BORRA DATOS: elimina las cajas, todos los movimientos de efectivo, reembolsos,
--  faltantes y sus abonos, los destinatarios del reporte, y de cada corte la información de
--  caja, recepción, entrega y arqueo (los totales de ventas de los cortes se conservan).
--  Los movimientos registrados con el corte nuevo NO regresan a movimientos_caja; los que
--  ya estaban en movimientos_caja siguen ahí intactos (la migración no los borró).
--  Respalda la base antes de correrlo (pg_dump).
--
--  Correrlo con el backend DETENIDO y con el código anterior a estos cambios: si el backend
--  nuevo arranca con ddl-auto=update, Hibernate vuelve a crear todo.
--
--  PostgreSQL. Idempotente: se puede correr más de una vez.
-- =====================================================================

BEGIN;

-- Tablas nuevas (primero las que dependen de otras).
DROP TABLE IF EXISTS shortage_payments;
DROP TABLE IF EXISTS shortages;
DROP TABLE IF EXISTS cash_movements;
DROP TABLE IF EXISTS reimbursements;
DROP TABLE IF EXISTS cash_report_recipients;

-- Columnas nuevas de cash_cuts (antes que cash_registers: cash_register_id apunta ahí).
ALTER TABLE cash_cuts DROP COLUMN IF EXISTS cash_register_id;
ALTER TABLE cash_cuts DROP COLUMN IF EXISTS previous_cut_id;
ALTER TABLE cash_cuts DROP COLUMN IF EXISTS expected_opening_amount;
ALTER TABLE cash_cuts DROP COLUMN IF EXISTS received_count_method;
ALTER TABLE cash_cuts DROP COLUMN IF EXISTS receive_note;
ALTER TABLE cash_cuts DROP COLUMN IF EXISTS handover_status;
ALTER TABLE cash_cuts DROP COLUMN IF EXISTS handover_difference;
ALTER TABLE cash_cuts DROP COLUMN IF EXISTS handover_resolution;
ALTER TABLE cash_cuts DROP COLUMN IF EXISTS handover_resolved_by_user_id;
ALTER TABLE cash_cuts DROP COLUMN IF EXISTS handover_resolved_at;
ALTER TABLE cash_cuts DROP COLUMN IF EXISTS handover_resolution_note;
ALTER TABLE cash_cuts DROP COLUMN IF EXISTS entries_total;
ALTER TABLE cash_cuts DROP COLUMN IF EXISTS withdrawals_total;
ALTER TABLE cash_cuts DROP COLUMN IF EXISTS reimbursements_total;
ALTER TABLE cash_cuts DROP COLUMN IF EXISTS loans_out_total;
ALTER TABLE cash_cuts DROP COLUMN IF EXISTS counted_amount;
ALTER TABLE cash_cuts DROP COLUMN IF EXISTS cash_difference;
ALTER TABLE cash_cuts DROP COLUMN IF EXISTS count_method;
ALTER TABLE cash_cuts DROP COLUMN IF EXISTS counted_by_user_id;
ALTER TABLE cash_cuts DROP COLUMN IF EXISTS count_evidence_path;
ALTER TABLE cash_cuts DROP COLUMN IF EXISTS handover_amount;
ALTER TABLE cash_cuts DROP COLUMN IF EXISTS arqueo_status;
ALTER TABLE cash_cuts DROP COLUMN IF EXISTS arqueo_resolution;
ALTER TABLE cash_cuts DROP COLUMN IF EXISTS arqueo_resolved_by_user_id;
ALTER TABLE cash_cuts DROP COLUMN IF EXISTS arqueo_resolved_at;
ALTER TABLE cash_cuts DROP COLUMN IF EXISTS arqueo_resolution_note;

DROP TABLE IF EXISTS cash_registers;

ALTER TABLE tiendas DROP COLUMN IF EXISTS blind_cash_count;
ALTER TABLE tiendas DROP COLUMN IF EXISTS report_to_supervisor;

COMMIT;
