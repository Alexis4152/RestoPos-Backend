-- =====================================================================
--  Rollback de migration_cobros_seguridad.sql
--
--  Antes de correrlo, despliega una versión del backend sin estos campos: con
--  ddl-auto=update, la versión nueva los volvería a crear.
--
--  OJO: las devoluciones son pagos negativos. Si ya se registró alguna, sigue en pagos y
--  sigue restando en cortes y reportes, pero pierde su liga al pago original. Se pierde
--  también qué transferencias se verificaron.
--
--  PostgreSQL. UNA SOLA SENTENCIA (bloque DO), se puede correr más de una vez.
-- =====================================================================
DO $$
BEGIN
  IF to_regclass('bk_cuentas_orden_cancelada') IS NOT NULL THEN
    UPDATE cuentas SET estado = 'PENDIENTE'
     WHERE id IN (SELECT cuenta_id FROM bk_cuentas_orden_cancelada) AND estado = 'CANCELADA';
  END IF;
  DROP TABLE IF EXISTS bk_cuentas_orden_cancelada;

  ALTER TABLE pagos DROP CONSTRAINT IF EXISTS uk_pagos_idempotencia;
  ALTER TABLE pagos DROP COLUMN IF EXISTS idempotency_key;
  ALTER TABLE pagos DROP COLUMN IF EXISTS pago_origen_id;
  ALTER TABLE pagos DROP COLUMN IF EXISTS verificacion;
  ALTER TABLE pagos DROP COLUMN IF EXISTS verificada_por;
  ALTER TABLE pagos DROP COLUMN IF EXISTS verificada_at;
  ALTER TABLE cuentas DROP COLUMN IF EXISTS total_devuelto;
  ALTER TABLE ordenes DROP COLUMN IF EXISTS pagado_at;
  ALTER TABLE ordenes DROP COLUMN IF EXISTS referencia_pago;

  RAISE NOTICE 'Seguridad de cobros deshecha';
END $$;
