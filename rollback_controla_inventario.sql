-- =====================================================================
--  Rollback de migration_controla_inventario.sql
--
--  Quita products.controla_inventario: Inventario vuelve a listar todos los productos.
--  Antes de correrlo, despliega una versión del backend sin el campo
--  Product.controlaInventario (con ddl-auto=update la versión nueva lo volvería a crear).
--  El stock y los movimientos de inventario no se tocan.
-- =====================================================================
ALTER TABLE products DROP COLUMN IF EXISTS controla_inventario;
