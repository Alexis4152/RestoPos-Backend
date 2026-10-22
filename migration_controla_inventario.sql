-- =====================================================================
--  Migración: productos que llevan inventario
--
--  Columna nueva: products.controla_inventario (boolean, default false)
--
--  No todo el menú lleva inventario (un platillo preparado no; un refresco o una cerveza
--  sí). Solo los productos marcados aparecen en Inventario, en las alertas de stock bajo y
--  en "buscar en tiendas asociadas", y solo a ellos se les ajusta stock. Se marca con la
--  casilla "Lleva inventario" del producto (Menú o Inventario).
--
--  Para no perder lo que ya se llevaba, quedan marcados los productos que hoy tienen stock,
--  stock mínimo o algún movimiento de inventario. Los demás quedan sin marcar.
--
--  PostgreSQL. UNA SOLA SENTENCIA (bloque DO) e idempotente: se puede correr más de una vez
--  (si Hibernate ya creó la columna con ddl-auto=update, solo se aplica el marcado inicial
--  la primera vez que se corre).
--  Deshacer: rollback_controla_inventario.sql
-- =====================================================================
DO $$
DECLARE
  v_marcados int := 0;
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                  WHERE table_name = 'products' AND column_name = 'controla_inventario') THEN
    ALTER TABLE products ADD COLUMN controla_inventario boolean NOT NULL DEFAULT false;
  END IF;
  ALTER TABLE products ALTER COLUMN controla_inventario SET DEFAULT false;
  UPDATE products SET controla_inventario = false WHERE controla_inventario IS NULL;
  ALTER TABLE products ALTER COLUMN controla_inventario SET NOT NULL;

  -- Marcado inicial (solo si todavía nadie tiene la casilla prendida).
  IF NOT EXISTS (SELECT 1 FROM products WHERE controla_inventario) THEN
    UPDATE products p SET controla_inventario = true
     WHERE p.stock <> 0 OR p.min_stock > 0
        OR EXISTS (SELECT 1 FROM inventory_movements m WHERE m.product_id = p.id);
    GET DIAGNOSTICS v_marcados = ROW_COUNT;
  END IF;

  RAISE NOTICE 'controla_inventario listo: % productos marcados como "lleva inventario"', v_marcados;
END $$;
