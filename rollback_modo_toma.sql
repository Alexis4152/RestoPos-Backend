-- =====================================================================
--  Rollback de migration_modo_toma.sql
--
--  Quita tiendas.modo_toma. Antes de correrlo, despliega una versión del backend que no
--  tenga el campo Tienda.modoToma: con ddl-auto=update, la versión nueva lo volvería a crear.
--  Se pierde qué tiendas estaban en modo rápido (todas vuelven al flujo detallado).
-- =====================================================================

BEGIN;

ALTER TABLE tiendas DROP CONSTRAINT IF EXISTS tiendas_modo_toma_check;
ALTER TABLE tiendas DROP COLUMN IF EXISTS modo_toma;

COMMIT;
