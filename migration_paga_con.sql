-- =====================================================================
--  Migración: "¿con cuánto pagas?" en pedidos web en efectivo
--
--  Columna nueva: ordenes.paga_con (numeric(12,2), opcional)
--
--  El cliente de la página web que paga en efectivo elige con qué billete paga (exacto o una
--  denominación que cubra el total). Se guarda para que el repartidor o la caja lleven el
--  cambio (paga_con - total). Los pedidos anteriores quedan en NULL (no lo indicaron).
--
--  PostgreSQL. UNA SOLA SENTENCIA (bloque DO), se puede correr más de una vez (si Hibernate ya
--  creó la columna con ddl-auto=update, se deja como está).
--  Deshacer: rollback_paga_con.sql
-- =====================================================================
DO $$
BEGIN
  ALTER TABLE ordenes ADD COLUMN IF NOT EXISTS paga_con numeric(12,2);
  RAISE NOTICE 'ordenes.paga_con lista';
END $$;
