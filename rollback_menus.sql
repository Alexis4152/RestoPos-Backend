-- Deshace migration_menus.sql: borra los menús (la tienda vuelve a vender todo el catálogo).
DROP TABLE IF EXISTS menu_productos;
DROP TABLE IF EXISTS menus;
