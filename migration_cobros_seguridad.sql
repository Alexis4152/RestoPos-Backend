-- =====================================================================
--  Migración: seguridad de cobros
--
--  Columnas nuevas:
--    pagos.idempotency_key   llave de cada intento de cobro (un reintento no crea otro pago)
--    pagos.pago_origen_id    devolución: pago negativo ligado al pago original
--    pagos.verificacion      transferencias: POR_VERIFICAR / VERIFICADA / NO_RECIBIDA
--    pagos.verificada_por, pagos.verificada_at
--    cuentas.total_devuelto  dinero regresado al cliente de esa cuenta
--    ordenes.pagado_at       cuándo quedó todo pagado (pagado y entregado son distintos)
--    ordenes.referencia_pago folio o voucher capturado por el repartidor
--
--  Limpieza de datos: las cuentas PENDIENTES sin pagos de órdenes ya CANCELADAS pasan a
--  CANCELADA (ya no se van a cobrar). Se respaldan en bk_cuentas_orden_cancelada.
--
--  Las transferencias anteriores quedan sin verificacion: no entran a "por verificar".
--
--  PostgreSQL. UNA SOLA SENTENCIA (bloque DO), se puede correr más de una vez.
--  Deshacer: rollback_cobros_seguridad.sql
-- =====================================================================
DO $$
DECLARE
  n integer;
BEGIN
  ALTER TABLE pagos ADD COLUMN IF NOT EXISTS idempotency_key varchar(64);
  ALTER TABLE pagos ADD COLUMN IF NOT EXISTS pago_origen_id bigint;
  ALTER TABLE pagos ADD COLUMN IF NOT EXISTS verificacion varchar(20);
  ALTER TABLE pagos ADD COLUMN IF NOT EXISTS verificada_por varchar(100);
  ALTER TABLE pagos ADD COLUMN IF NOT EXISTS verificada_at timestamp;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_pagos_idempotencia') THEN
    ALTER TABLE pagos ADD CONSTRAINT uk_pagos_idempotencia UNIQUE (cuenta_id, idempotency_key);
  END IF;

  ALTER TABLE cuentas ADD COLUMN IF NOT EXISTS total_devuelto numeric(12,2);
  UPDATE cuentas SET total_devuelto = 0 WHERE total_devuelto IS NULL;
  ALTER TABLE cuentas ALTER COLUMN total_devuelto SET DEFAULT 0;
  ALTER TABLE cuentas ALTER COLUMN total_devuelto SET NOT NULL;

  ALTER TABLE ordenes ADD COLUMN IF NOT EXISTS pagado_at timestamp;
  ALTER TABLE ordenes ADD COLUMN IF NOT EXISTS referencia_pago varchar(100);
  UPDATE ordenes SET pagado_at = closed_at WHERE estado = 'PAGADA' AND pagado_at IS NULL;

  CREATE TABLE IF NOT EXISTS bk_cuentas_orden_cancelada (cuenta_id bigint PRIMARY KEY);
  INSERT INTO bk_cuentas_orden_cancelada (cuenta_id)
  SELECT c.id FROM cuentas c JOIN ordenes o ON o.id = c.orden_id
   WHERE c.estado = 'PENDIENTE' AND o.estado = 'CANCELADA' AND c.total_pagado = 0
  ON CONFLICT DO NOTHING;
  UPDATE cuentas SET estado = 'CANCELADA'
   WHERE id IN (SELECT cuenta_id FROM bk_cuentas_orden_cancelada) AND estado = 'PENDIENTE';
  GET DIAGNOSTICS n = ROW_COUNT;

  RAISE NOTICE 'Seguridad de cobros lista (% cuentas de órdenes canceladas cerradas)', n;
END $$;
