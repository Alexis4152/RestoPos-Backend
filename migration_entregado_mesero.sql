-- =====================================================================
--  Migración: estado ENTREGADO_MESERO en orden_detalles
--  Cocina marca ENTREGADO_MESERO (se lo pasó al mesero); el mesero marca
--  ENTREGADO (entregado a mesa / para llevar). 'ENTREGADO_MESERO' mide 16
--  caracteres, así que la columna pasa de VARCHAR(15) a VARCHAR(20).
--  Idempotente: se puede correr más de una vez.
-- =====================================================================
ALTER TABLE orden_detalles ALTER COLUMN estado TYPE VARCHAR(20);

ALTER TABLE orden_detalles DROP CONSTRAINT IF EXISTS orden_detalles_estado_check;
ALTER TABLE orden_detalles ADD CONSTRAINT orden_detalles_estado_check
    CHECK (estado IN ('PENDIENTE','PREPARANDO','LISTO','ENTREGADO_MESERO','ENTREGADO','CANCELADO'));
