-- =====================================================================
--  Migración: quién imprime la cuenta por pagar (configurable por tienda)
--
--  Columna nueva: tiendas.imprime_cuenta ('CAJERO' | 'MESERO' | 'AMBOS', default 'CAJERO')
--
--  Define quién puede imprimir la cuenta por pagar (las 2 copias cliente/comercio): el cajero
--  (quien cobra), el mesero o ambos. ADMIN, SUPERVISOR y SUPER_ADMIN siempre pueden. Solo la
--  cambia el SUPER_ADMIN (Datos de la tienda → Impresión de la cuenta). Todas las tiendas
--  quedan en 'CAJERO'.
--
--  PostgreSQL. UNA SOLA SENTENCIA (bloque DO), se puede correr más de una vez (si Hibernate
--  ya creó la columna con ddl-auto=update, solo se asegura el default y la restricción).
--  Deshacer: rollback_imprime_cuenta.sql
-- =====================================================================
DO $$
BEGIN
  ALTER TABLE tiendas ADD COLUMN IF NOT EXISTS imprime_cuenta varchar(10);
  UPDATE tiendas SET imprime_cuenta = 'CAJERO'
   WHERE imprime_cuenta IS NULL OR imprime_cuenta NOT IN ('CAJERO', 'MESERO', 'AMBOS');
  ALTER TABLE tiendas ALTER COLUMN imprime_cuenta SET DEFAULT 'CAJERO';
  ALTER TABLE tiendas ALTER COLUMN imprime_cuenta SET NOT NULL;

  ALTER TABLE tiendas DROP CONSTRAINT IF EXISTS tiendas_imprime_cuenta_check;
  ALTER TABLE tiendas ADD CONSTRAINT tiendas_imprime_cuenta_check
    CHECK (imprime_cuenta IN ('CAJERO', 'MESERO', 'AMBOS'));

  RAISE NOTICE 'Impresión de cuenta lista (todas las tiendas: CAJERO)';
END $$;
