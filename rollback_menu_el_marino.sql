-- =====================================================================
--  Rollback de seed_menu_el_marino.sql
--
--  UNA SOLA SENTENCIA (un bloque DO): se ejecuta completa o no se ejecuta nada.
--  Quita de la tienda 3 las 9 categorías del menú El Marino, sus productos y modificadores.
--  Lo que ya se usó en alguna orden NO se borra (rompería el historial de ventas): se da de
--  baja (is_active = false, deleted_at = ahora), igual que al eliminarlo desde Menú.
--
--  Para otra tienda, cambia el 3 en la línea "v_tienda CONSTANT bigint := 3".
-- =====================================================================
DO $$
DECLARE
  v_tienda CONSTANT bigint := 3;
  v_cats CONSTANT text[] := ARRAY['Cocteles','Pescadillas','Caldos','Pulpos','Camarones','Filetes','Mojarras','Bebidas','Cervezas'];
  v_prods bigint[];
  v_usados bigint[];
  v_borrados int;
  v_baja int;
BEGIN
  SELECT coalesce(array_agg(p.id), '{}') INTO v_prods
    FROM products p JOIN categories c ON c.id = p.category_id
   WHERE p.tienda_id = v_tienda AND c.tienda_id = v_tienda AND c.name = ANY (v_cats);
  SELECT coalesce(array_agg(DISTINCT d.producto_id), '{}') INTO v_usados
    FROM orden_detalles d WHERE d.producto_id = ANY (v_prods);

  -- Sin ventas: se borran con sus modificadores.
  DELETE FROM modificador_opciones WHERE grupo_id IN (
    SELECT id FROM modificador_grupos WHERE producto_id = ANY (v_prods) AND NOT producto_id = ANY (v_usados));
  DELETE FROM modificador_grupos WHERE producto_id = ANY (v_prods) AND NOT producto_id = ANY (v_usados);
  DELETE FROM inventory_movements WHERE product_id = ANY (v_prods) AND NOT product_id = ANY (v_usados);
  DELETE FROM products WHERE id = ANY (v_prods) AND NOT id = ANY (v_usados);
  GET DIAGNOSTICS v_borrados = ROW_COUNT;

  -- Con ventas: baja lógica.
  UPDATE products SET is_active = false, deleted_at = now(), updated_at = now()
   WHERE id = ANY (v_usados) AND is_active;
  GET DIAGNOSTICS v_baja = ROW_COUNT;

  -- Categorías: se borran si ya no tienen productos; si no, baja lógica.
  UPDATE categories c SET is_active = false, deleted_at = now(), updated_at = now()
   WHERE c.tienda_id = v_tienda AND c.name = ANY (v_cats)
     AND EXISTS (SELECT 1 FROM products p WHERE p.category_id = c.id);
  DELETE FROM categories c
   WHERE c.tienda_id = v_tienda AND c.name = ANY (v_cats)
     AND NOT EXISTS (SELECT 1 FROM products p WHERE p.category_id = c.id);

  RAISE NOTICE 'Tienda %: % productos borrados, % dados de baja (ya tenían ventas)', v_tienda, v_borrados, v_baja;
END $$;
