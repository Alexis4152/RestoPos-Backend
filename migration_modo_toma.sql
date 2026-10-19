-- =====================================================================
--  Migración: modo de toma de orden por tienda (rápido / detallado)
--
--  Columna nueva: tiendas.modo_toma  ('RAPIDO' | 'DETALLADO', default 'DETALLADO')
--
--  Es el modo con el que arrancan los meseros de la tienda ("Datos de la tienda"); cada
--  mesero puede cambiarlo en su dispositivo sin tocar este valor. Las tiendas existentes
--  quedan en DETALLADO: nadie ve un cambio hasta que el admin lo active.
--
--  PostgreSQL. Idempotente: se puede correr más de una vez (si Hibernate ya creó la columna
--  con ddl-auto=update, se deja como está y solo se asegura el default y la restricción).
--  Deshacer: rollback_modo_toma.sql
-- =====================================================================

BEGIN;

ALTER TABLE tiendas ADD COLUMN IF NOT EXISTS modo_toma VARCHAR(10);
UPDATE tiendas SET modo_toma = 'DETALLADO' WHERE modo_toma IS NULL OR modo_toma NOT IN ('RAPIDO', 'DETALLADO');
ALTER TABLE tiendas ALTER COLUMN modo_toma SET DEFAULT 'DETALLADO';
ALTER TABLE tiendas ALTER COLUMN modo_toma SET NOT NULL;

ALTER TABLE tiendas DROP CONSTRAINT IF EXISTS tiendas_modo_toma_check;
ALTER TABLE tiendas ADD CONSTRAINT tiendas_modo_toma_check CHECK (modo_toma IN ('RAPIDO', 'DETALLADO'));

COMMIT;
