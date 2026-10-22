-- =====================================================================
--  Migración: descuento de inventario al vender
--
--  Los productos con "Lleva inventario" descuentan su stock al agregarse a una orden (POS o
--  página web), lo regresan al cancelarse, y se agotan solos al llegar a 0. Cada movimiento
--  lleva la referencia OD:<línea de orden> para no regresar más de lo descontado.
--
--  Cambios:
--    - inventory_movements.user_id deja de ser obligatorio: la venta de un pedido de la
--      página web no tiene usuario del personal (la hace el cliente).
--    - Índice en inventory_movements.reference (se consulta al cancelar o editar).
--
--  OBLIGATORIA en producción: Hibernate (ddl-auto=update) no quita un NOT NULL, y sin esto
--  un pedido web con un producto inventariado fallaría al guardarse.
--  PostgreSQL. UNA SOLA SENTENCIA (bloque DO), se puede correr más de una vez.
--  Deshacer: rollback_inventario_ventas.sql
-- =====================================================================
DO $$
BEGIN
  ALTER TABLE inventory_movements ALTER COLUMN user_id DROP NOT NULL;
  CREATE INDEX IF NOT EXISTS idx_inventory_movements_reference ON inventory_movements (reference);
  RAISE NOTICE 'inventory_movements listo para ventas de la página web';
END $$;
