DO $$
-- =====================================================================
--  Carga del menú "El Marino Cevichería" en la tienda 3
--
--  UNA SOLA SENTENCIA (un bloque DO): se ejecuta completa o no se ejecuta nada, y funciona
--  igual en DBeaver, pgAdmin o psql aunque el cliente parta el script en cada ";".
--
--  Crea 9 categorías (Cocteles, Pescadillas, Caldos, Pulpos, Camarones, Filetes, Mojarras,
--  Bebidas, Cervezas), sus 64 productos con el precio del menú y estos modificadores
--  (todos obligatorios y de una sola opción):
--    - Cocteles: "Tamaño" (Chico / Grande +diferencia). El precio base es el del chico.
--    - Caldos: "Caldo" (Rojo / Verde).
--    - Camarones mixtos: "Preparación 1" y "Preparación 2" (los 2 ingredientes).
--    - Agua natural y agua de sabor: "Tamaño" con su diferencia de precio.
--
--  Para otra tienda, cambia el 3 en la línea "v_tienda CONSTANT bigint := 3".
--  Se puede correr más de una vez: lo que ya existe (misma categoría / mismo producto en esa
--  categoría, activos) se deja como está.
--  Deshacer: rollback_menu_el_marino.sql
-- =====================================================================
DECLARE
  v_tienda CONSTANT bigint := 3;
  ACOMP CONSTANT text := 'Acompañado de arroz, verdura y tortillas a mano.';
  MIXTO CONSTANT text[] := ARRAY['Al mojo', 'Al ajillo', 'Empanizados', 'A la diabla', 'Al coco'];
  r record;
  v_cat bigint;
  v_prod bigint;
  v_grupo bigint;
  v_nuevos int := 0;
  g int;
  g_nombre text;
  g_ops text[];
  g_extras numeric[];
BEGIN
  IF NOT EXISTS (SELECT 1 FROM tiendas WHERE id = v_tienda) THEN
    RAISE EXCEPTION 'No existe la tienda %', v_tienda;
  END IF;

  FOR r IN SELECT * FROM (VALUES
    -- categoría, descripción de la categoría, producto, descripción, precio,
    -- grupo 1 (nombre, opciones, extras), grupo 2 (nombre, opciones, extras)
    (1, 'Cocteles', 'Grande o chico.', 'Vuelve a la vida', 'Surtido', 120, 'Tamaño', ARRAY['Chico','Grande'], ARRAY[0,60]::numeric[], NULL, NULL::text[], NULL::numeric[]),
    (1, 'Cocteles', 'Grande o chico.', 'Campechana', 'Pulpo, camarón y ostión', 120, 'Tamaño', ARRAY['Chico','Grande'], ARRAY[0,40]::numeric[], NULL, NULL, NULL),
    (1, 'Cocteles', 'Grande o chico.', 'Coctel de camarón', NULL, 140, 'Tamaño', ARRAY['Chico','Grande'], ARRAY[0,40]::numeric[], NULL, NULL, NULL),
    (1, 'Cocteles', 'Grande o chico.', 'Coctel de pulpo', NULL, 140, 'Tamaño', ARRAY['Chico','Grande'], ARRAY[0,40]::numeric[], NULL, NULL, NULL),
    (1, 'Cocteles', 'Grande o chico.', 'Coctel de pescado', NULL, 120, 'Tamaño', ARRAY['Chico','Grande'], ARRAY[0,40]::numeric[], NULL, NULL, NULL),
    (1, 'Cocteles', 'Grande o chico.', 'Coctel de surimi', NULL, 120, 'Tamaño', ARRAY['Chico','Grande'], ARRAY[0,30]::numeric[], NULL, NULL, NULL),
    (1, 'Cocteles', 'Grande o chico.', 'Coctel de ostión', NULL, 120, 'Tamaño', ARRAY['Chico','Grande'], ARRAY[0,30]::numeric[], NULL, NULL, NULL),

    (2, 'Pescadillas', 'Orden de 6 piezas.', 'Pescadillas sencillas', NULL, 25, NULL, NULL, NULL, NULL, NULL, NULL),
    (2, 'Pescadillas', 'Orden de 6 piezas.', 'Pescadillas ahogadas', 'Jugo de jitomate y salsas', 70, NULL, NULL, NULL, NULL, NULL, NULL),
    (2, 'Pescadillas', 'Orden de 6 piezas.', 'Pescadillas ahogadas con mariscos', NULL, 100, NULL, NULL, NULL, NULL, NULL, NULL),
    (2, 'Pescadillas', 'Orden de 6 piezas.', 'Pescadillas especiales', 'Aguacate y queso', 80, NULL, NULL, NULL, NULL, NULL, NULL),
    (2, 'Pescadillas', 'Orden de 6 piezas.', 'Pescadillas de camarón', 'Para preparar', 90, NULL, NULL, NULL, NULL, NULL, NULL),

    (3, 'Caldos', 'Rojo o verde.', 'Caldo de camarón', NULL, 120, 'Caldo', ARRAY['Rojo','Verde'], ARRAY[0,0]::numeric[], NULL, NULL, NULL),
    (3, 'Caldos', 'Rojo o verde.', 'Sopa de mariscos', 'Jaiba, pescado y camarón', 150, 'Caldo', ARRAY['Rojo','Verde'], ARRAY[0,0]::numeric[], NULL, NULL, NULL),
    (3, 'Caldos', 'Rojo o verde.', 'Caldo de pescado', NULL, 120, 'Caldo', ARRAY['Rojo','Verde'], ARRAY[0,0]::numeric[], NULL, NULL, NULL),
    (3, 'Caldos', 'Rojo o verde.', 'Caldo de mojarra', NULL, 180, 'Caldo', ARRAY['Rojo','Verde'], ARRAY[0,0]::numeric[], NULL, NULL, NULL),

    (4, 'Pulpos', ACOMP, 'Pulpo enamorado', 'Pico de gallo y mayonesa', 180, NULL, NULL, NULL, NULL, NULL, NULL),
    (4, 'Pulpos', ACOMP, 'Pulpo al ajillo', 'Ajo y mantequilla', 180, NULL, NULL, NULL, NULL, NULL, NULL),
    (4, 'Pulpos', ACOMP, 'Pulpo al mojo', 'Enchilado', 180, NULL, NULL, NULL, NULL, NULL, NULL),
    (4, 'Pulpos', ACOMP, 'Pulpo a la diabla', 'Habanero', 180, NULL, NULL, NULL, NULL, NULL, NULL),
    (4, 'Pulpos', ACOMP, 'Pulpo acitronado', 'Mantequilla, cebolla y cilantro', 180, NULL, NULL, NULL, NULL, NULL, NULL),

    (5, 'Camarones', ACOMP, 'Camarones al mojo', 'Enchilados', 180, NULL, NULL, NULL, NULL, NULL, NULL),
    (5, 'Camarones', ACOMP, 'Camarones al ajillo', 'Ajo y mantequilla', 160, NULL, NULL, NULL, NULL, NULL, NULL),
    (5, 'Camarones', ACOMP, 'Camarones empanizados', NULL, 180, NULL, NULL, NULL, NULL, NULL, NULL),
    (5, 'Camarones', ACOMP, 'Camarones a la diabla', 'Enchilados con habanero', 180, NULL, NULL, NULL, NULL, NULL, NULL),
    (5, 'Camarones', ACOMP, 'Camarones al coco', NULL, 180, NULL, NULL, NULL, NULL, NULL, NULL),
    (5, 'Camarones', ACOMP, 'Camarones a la momia', NULL, 220, NULL, NULL, NULL, NULL, NULL, NULL),
    (5, 'Camarones', ACOMP, 'Camarones mixtos', '2 preparaciones a elegir', 185, 'Preparación 1', MIXTO, ARRAY[0,0,0,0,0]::numeric[], 'Preparación 2', MIXTO, ARRAY[0,0,0,0,0]::numeric[]),
    (5, 'Camarones', ACOMP, 'Tres deseos', '9 piezas', 200, NULL, NULL, NULL, NULL, NULL, NULL),

    (6, 'Filetes', ACOMP, 'Filete empanizado', NULL, 150, NULL, NULL, NULL, NULL, NULL, NULL),
    (6, 'Filetes', ACOMP, 'Filete al mojo', 'Salsa de árbol', 150, NULL, NULL, NULL, NULL, NULL, NULL),
    (6, 'Filetes', ACOMP, 'Filete a la diabla', 'Habanero', 150, NULL, NULL, NULL, NULL, NULL, NULL),
    (6, 'Filetes', ACOMP, 'Filete asado', 'Mantequilla y especias', 150, NULL, NULL, NULL, NULL, NULL, NULL),
    (6, 'Filetes', ACOMP, 'Filete empapelado', NULL, 170, NULL, NULL, NULL, NULL, NULL, NULL),
    (6, 'Filetes', ACOMP, 'Filete relleno', 'Pulpo y camarón', 190, NULL, NULL, NULL, NULL, NULL, NULL),
    (6, 'Filetes', ACOMP, 'Filete vegetariano', 'Calabaza, zanahoria, espinaca y queso', 190, NULL, NULL, NULL, NULL, NULL, NULL),
    (6, 'Filetes', ACOMP, 'Filete hawaiano', 'Queso, piña y jamón', 190, NULL, NULL, NULL, NULL, NULL, NULL),

    (7, 'Mojarras', 'Peso aproximado 500 g. ' || ACOMP, 'Mojarra frita', NULL, 180, NULL, NULL, NULL, NULL, NULL, NULL),
    (7, 'Mojarras', 'Peso aproximado 500 g. ' || ACOMP, 'Mojarra al ajillo', 'Ajo recaudo', 180, NULL, NULL, NULL, NULL, NULL, NULL),
    (7, 'Mojarras', 'Peso aproximado 500 g. ' || ACOMP, 'Mojarra al mojo', 'Salsa de árbol', 180, NULL, NULL, NULL, NULL, NULL, NULL),
    (7, 'Mojarras', 'Peso aproximado 500 g. ' || ACOMP, 'Mojarra a la diabla', 'Habanero', 180, NULL, NULL, NULL, NULL, NULL, NULL),
    (7, 'Mojarras', 'Peso aproximado 500 g. ' || ACOMP, 'Mojarra en salsa macha', NULL, 190, NULL, NULL, NULL, NULL, NULL, NULL),
    (7, 'Mojarras', 'Peso aproximado 500 g. ' || ACOMP, 'Mojarra a la veracruzana', NULL, 190, NULL, NULL, NULL, NULL, NULL, NULL),
    (7, 'Mojarras', 'Peso aproximado 500 g. ' || ACOMP, 'Mojarra empapelada', NULL, 200, NULL, NULL, NULL, NULL, NULL, NULL),
    (7, 'Mojarras', 'Peso aproximado 500 g. ' || ACOMP, 'Mojarra rellena de mariscos', NULL, 200, NULL, NULL, NULL, NULL, NULL, NULL),
    (7, 'Mojarras', 'Peso aproximado 500 g. ' || ACOMP, 'Mojarra a la talla', NULL, 220, NULL, NULL, NULL, NULL, NULL, NULL),

    (8, 'Bebidas', NULL, 'Coca', NULL, 25, NULL, NULL, NULL, NULL, NULL, NULL),
    (8, 'Bebidas', NULL, 'Refresco de sabor', NULL, 25, NULL, NULL, NULL, NULL, NULL, NULL),
    (8, 'Bebidas', NULL, 'Agua mineral', NULL, 35, NULL, NULL, NULL, NULL, NULL, NULL),
    (8, 'Bebidas', NULL, 'Agua natural', NULL, 15, 'Tamaño', ARRAY['Chica','Grande'], ARRAY[0,10]::numeric[], NULL, NULL, NULL),
    (8, 'Bebidas', NULL, 'Jugo', NULL, 30, NULL, NULL, NULL, NULL, NULL, NULL),
    (8, 'Bebidas', NULL, 'Tehuacán', NULL, 45, NULL, NULL, NULL, NULL, NULL, NULL),
    (8, 'Bebidas', NULL, 'Limonada', NULL, 45, NULL, NULL, NULL, NULL, NULL, NULL),
    (8, 'Bebidas', NULL, 'Naranjada', NULL, 45, NULL, NULL, NULL, NULL, NULL, NULL),
    (8, 'Bebidas', NULL, 'Agua de sabor', NULL, 25, 'Tamaño', ARRAY['Chica','Litro','Jarra'], ARRAY[0,15,45]::numeric[], NULL, NULL, NULL),

    (9, 'Cervezas', NULL, 'Victoria', NULL, 35, NULL, NULL, NULL, NULL, NULL, NULL),
    (9, 'Cervezas', NULL, 'Corona', NULL, 35, NULL, NULL, NULL, NULL, NULL, NULL),
    (9, 'Cervezas', NULL, 'Modelo especial', NULL, 45, NULL, NULL, NULL, NULL, NULL, NULL),
    (9, 'Cervezas', NULL, 'Corona light', NULL, 45, NULL, NULL, NULL, NULL, NULL, NULL),
    (9, 'Cervezas', NULL, 'Caribe', NULL, 40, NULL, NULL, NULL, NULL, NULL, NULL),
    (9, 'Cervezas', NULL, 'Michelada', NULL, 45, NULL, NULL, NULL, NULL, NULL, NULL),
    (9, 'Cervezas', NULL, 'Cubana', NULL, 45, NULL, NULL, NULL, NULL, NULL, NULL),
    (9, 'Cervezas', NULL, 'Viagra de ostión', NULL, 70, NULL, NULL, NULL, NULL, NULL, NULL),
    (9, 'Cervezas', NULL, 'Viagra de camarón', NULL, 90, NULL, NULL, NULL, NULL, NULL, NULL)
  ) AS t(orden_cat, categoria, cat_desc, nombre, descr, precio, g1, g1_ops, g1_extras, g2, g2_ops, g2_extras)
  LOOP
    -- Categoría (se crea una vez).
    SELECT id INTO v_cat FROM categories
     WHERE tienda_id = v_tienda AND lower(name) = lower(r.categoria) AND is_active AND deleted_at IS NULL LIMIT 1;
    IF v_cat IS NULL THEN
      INSERT INTO categories (name, description, tienda_id, is_active, availability_mode, created_at, updated_at)
      VALUES (r.categoria, r.cat_desc, v_tienda, true, 'AUTO', now(), now()) RETURNING id INTO v_cat;
    END IF;

    -- Producto: si ya existe en esa categoría, se deja como está (y sus modificadores también).
    SELECT id INTO v_prod FROM products
     WHERE tienda_id = v_tienda AND category_id = v_cat AND lower(name) = lower(r.nombre) AND is_active AND deleted_at IS NULL LIMIT 1;
    CONTINUE WHEN v_prod IS NOT NULL;
    INSERT INTO products (name, description, price, cost, stock, min_stock, unit, disponible, category_id, tienda_id, is_active, created_at, updated_at)
    VALUES (r.nombre, r.descr, r.precio, 0, 0, 0, 'pieza', true, v_cat, v_tienda, true, now(), now()) RETURNING id INTO v_prod;
    v_nuevos := v_nuevos + 1;

    -- Hasta 2 grupos de modificadores, obligatorios y de una opción.
    FOR g IN 1 .. 2 LOOP
      g_nombre := CASE g WHEN 1 THEN r.g1 ELSE r.g2 END;
      g_ops    := CASE g WHEN 1 THEN r.g1_ops ELSE r.g2_ops END;
      g_extras := CASE g WHEN 1 THEN r.g1_extras ELSE r.g2_extras END;
      CONTINUE WHEN g_nombre IS NULL;
      INSERT INTO modificador_grupos (producto_id, nombre, tipo, obligatorio, orden, is_active, created_at, updated_at)
      VALUES (v_prod, g_nombre, 'UNICO', true, g - 1, true, now(), now()) RETURNING id INTO v_grupo;
      FOR i IN 1 .. array_length(g_ops, 1) LOOP
        INSERT INTO modificador_opciones (grupo_id, nombre, precio_extra, orden, is_active, created_at, updated_at)
        VALUES (v_grupo, g_ops[i], g_extras[i], i - 1, true, now(), now());
      END LOOP;
    END LOOP;
  END LOOP;

  RAISE NOTICE 'Menú El Marino en la tienda %: % productos nuevos (de 64)', v_tienda, v_nuevos;
END $$;

-- Verificación (sale como tabla de resultados): debe dar 9 categorías y 64 productos.
SELECT (SELECT count(*) FROM categories WHERE tienda_id = 3 AND is_active
          AND name IN ('Cocteles','Pescadillas','Caldos','Pulpos','Camarones','Filetes','Mojarras','Bebidas','Cervezas')) AS categorias,
       (SELECT count(*) FROM products p JOIN categories c ON c.id = p.category_id
         WHERE p.tienda_id = 3 AND p.is_active
           AND c.name IN ('Cocteles','Pescadillas','Caldos','Pulpos','Camarones','Filetes','Mojarras','Bebidas','Cervezas')) AS productos;
