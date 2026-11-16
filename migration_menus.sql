-- =====================================================================
--  Migración: menús con nombre y "menú de hoy"
--
--  Tablas nuevas:
--    menus           — menú con nombre por tienda ("Entre semana", "Fin de semana"...);
--                      hoy = true marca el menú de hoy (a lo más uno por tienda).
--    menu_productos  — platillos de cada menú.
--
--  Mientras una tienda tenga menú de hoy, solo sus platillos se pueden ordenar (POS y página
--  web). Sin menú de hoy se vende todo el catálogo, como antes: las tiendas existentes no
--  cambian nada hasta que creen su primer menú.
--
--  PostgreSQL. UNA SOLA SENTENCIA (bloque DO), se puede correr más de una vez (si Hibernate
--  ya creó las tablas con ddl-auto=update, solo asegura índices y la regla de un solo "hoy").
--  Deshacer: rollback_menus.sql
-- =====================================================================
DO $$
BEGIN
  CREATE TABLE IF NOT EXISTS menus (
    id                  BIGSERIAL PRIMARY KEY,
    nombre              VARCHAR(100) NOT NULL,
    tienda_id           BIGINT NOT NULL REFERENCES tiendas(id),
    hoy                 BOOLEAN NOT NULL DEFAULT FALSE,
    updated_by_user_id  BIGINT REFERENCES users(id),
    created_at          TIMESTAMP,
    updated_at          TIMESTAMP
  );
  CREATE TABLE IF NOT EXISTS menu_productos (
    menu_id     BIGINT NOT NULL REFERENCES menus(id),
    product_id  BIGINT NOT NULL REFERENCES products(id),
    PRIMARY KEY (menu_id, product_id)
  );
  CREATE INDEX IF NOT EXISTS idx_menus_tienda ON menus (tienda_id);
  -- A lo más un menú de hoy por tienda.
  CREATE UNIQUE INDEX IF NOT EXISTS uq_menus_hoy_por_tienda ON menus (tienda_id) WHERE hoy;
END $$;
