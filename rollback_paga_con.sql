-- =====================================================================
--  Rollback de migration_paga_con.sql
--
--  Quita ordenes.paga_con (se pierde con cuánto dijo pagar cada cliente). Antes de correrlo,
--  despliega una versión del backend sin el campo Orden.pagaCon (con ddl-auto=update la
--  versión nueva lo volvería a crear).
-- =====================================================================
ALTER TABLE ordenes DROP COLUMN IF EXISTS paga_con;
