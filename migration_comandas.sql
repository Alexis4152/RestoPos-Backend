-- =====================================================================
--  Migración: envío a cocina configurable + cola de comandas por imprimir
--
--  1. Columna nueva: tiendas.enviar_a_cocina (boolean, default true)
--     true  → botón "Enviar a cocina"; la comanda sale en la impresora de COCINA.
--     false → botón "Crear orden"; la comanda sale en la impresora normal de tickets.
--     Solo la cambia el SUPER_ADMIN (Datos de la tienda → Envío a cocina). Todas las tiendas
--     quedan en true (el botón sigue diciendo "Enviar a cocina", como hasta ahora).
--
--  2. Tabla nueva: comandas — cola que consulta la "estación de impresión" (computadora con
--     QZ Tray) para imprimir cada comanda que mandan los meseros.
--
--  PostgreSQL. UNA SOLA SENTENCIA (bloque DO), se puede correr más de una vez (si Hibernate
--  ya creó la columna/tabla con ddl-auto=update, solo asegura default y restricciones).
--  Deshacer: rollback_comandas.sql
-- =====================================================================
DO $$
BEGIN
  ALTER TABLE tiendas ADD COLUMN IF NOT EXISTS enviar_a_cocina boolean;
  UPDATE tiendas SET enviar_a_cocina = true WHERE enviar_a_cocina IS NULL;
  ALTER TABLE tiendas ALTER COLUMN enviar_a_cocina SET DEFAULT true;
  ALTER TABLE tiendas ALTER COLUMN enviar_a_cocina SET NOT NULL;

  CREATE TABLE IF NOT EXISTS comandas (
    id          bigserial PRIMARY KEY,
    tienda_id   bigint        NOT NULL,
    orden_id    bigint        NOT NULL,
    destino     varchar(10)   NOT NULL,
    detalle_ids varchar(2000) NOT NULL,
    enviada_por varchar(100),
    tomada_at   timestamp(6),
    intentos    integer       NOT NULL DEFAULT 0,
    created_at  timestamp(6)
  );
  CREATE INDEX IF NOT EXISTS idx_comandas_pendientes ON comandas (tienda_id, tomada_at);

  ALTER TABLE comandas DROP CONSTRAINT IF EXISTS comandas_destino_check;
  ALTER TABLE comandas ADD CONSTRAINT comandas_destino_check CHECK (destino IN ('COCINA', 'CAJA'));

  RAISE NOTICE 'Envío a cocina y cola de comandas listos';
END $$;
