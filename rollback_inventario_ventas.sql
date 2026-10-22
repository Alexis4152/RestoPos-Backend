-- =====================================================================
--  Rollback de migration_inventario_ventas.sql
--
--  Vuelve a hacer obligatorio inventory_movements.user_id. Los movimientos de ventas web
--  (sin usuario) se asignan al usuario más antiguo de su tienda para no perder el historial.
--  Antes de correrlo, despliega una versión del backend sin el descuento al vender.
--  UNA SOLA SENTENCIA (bloque DO).
-- =====================================================================
DO $$
BEGIN
  UPDATE inventory_movements m
     SET user_id = (SELECT u.id FROM users u JOIN products p ON p.id = m.product_id
                     WHERE u.tienda_id = p.tienda_id ORDER BY u.id LIMIT 1)
   WHERE m.user_id IS NULL;
  UPDATE inventory_movements SET user_id = (SELECT min(id) FROM users) WHERE user_id IS NULL;
  ALTER TABLE inventory_movements ALTER COLUMN user_id SET NOT NULL;
  DROP INDEX IF EXISTS idx_inventory_movements_reference;
END $$;
