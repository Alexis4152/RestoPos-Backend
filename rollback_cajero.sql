-- =====================================================================
--  Rollback de migration_cajero.sql
--
--  * Devuelve CASH_CUTS a los roles MESERO/REPARTIDOR que lo tenían (respaldo
--    bk_cajero_role_sections) y borra el respaldo.
--  * Borra los roles CAJERO que no tengan usuarios asignados (los que sí tienen se dejan:
--    reasigna esos usuarios primero si quieres quitarlos).
--  Ojo: si la aplicación nueva sigue desplegada, vuelve a crear el rol CAJERO al arrancar.
--
--  PostgreSQL. UNA SOLA SENTENCIA (bloque DO).
-- =====================================================================
DO $$
DECLARE
  n integer;
BEGIN
  IF to_regclass('bk_cajero_role_sections') IS NOT NULL THEN
    INSERT INTO role_sections (role_id, section)
    SELECT b.role_id, b.section FROM bk_cajero_role_sections b
     WHERE EXISTS (SELECT 1 FROM roles r WHERE r.id = b.role_id)
       AND NOT EXISTS (SELECT 1 FROM role_sections rs WHERE rs.role_id = b.role_id AND rs.section = b.section);
    GET DIAGNOSTICS n = ROW_COUNT;
    RAISE NOTICE 'CASH_CUTS devuelto a % rol(es)', n;
    DROP TABLE bk_cajero_role_sections;
  END IF;

  DELETE FROM role_sections rs USING roles r
   WHERE r.id = rs.role_id AND r.name = 'CAJERO'
     AND NOT EXISTS (SELECT 1 FROM users u WHERE u.role_id = r.id);
  DELETE FROM roles r WHERE r.name = 'CAJERO' AND NOT EXISTS (SELECT 1 FROM users u WHERE u.role_id = r.id);
  GET DIAGNOSTICS n = ROW_COUNT;
  RAISE NOTICE 'Roles CAJERO sin usuarios borrados: %', n;
END $$;
