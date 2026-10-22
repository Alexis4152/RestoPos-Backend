-- =====================================================================
--  Migración: perfil CAJERO — el mesero y el repartidor ya no cobran
--
--  Cobrar (registrar pagos, recibir el dinero de los repartidores) exige la sección
--  CASH_CUTS. A partir de ahora:
--    * MESERO: genera e imprime la cuenta (2 copias: cliente y comercio); el cliente paga en
--      caja. Se le quita CASH_CUTS.
--    * REPARTIDOR: solo entrega; el dinero lo recibe la caja. Se le quita CASH_CUTS.
--    * CAJERO (rol nuevo por tienda): lo crea la aplicación sola al arrancar
--      (RepartidorRoleBackfill -> RoleService#ensureCajeroRole); no hace falta insertarlo aquí.
--
--  Lo que se quita se respalda en la tabla bk_cajero_role_sections para poder deshacerlo.
--  Si en una tienda chica el mesero SÍ debe cobrar, el admin le vuelve a activar
--  "Cortes de caja" desde Roles y permisos.
--
--  PostgreSQL. UNA SOLA SENTENCIA (bloque DO), se puede correr más de una vez.
--  Deshacer: rollback_cajero.sql
-- =====================================================================
DO $$
DECLARE
  n integer;
BEGIN
  CREATE TABLE IF NOT EXISTS bk_cajero_role_sections (role_id bigint NOT NULL, section varchar(20) NOT NULL,
                                                      PRIMARY KEY (role_id, section));
  INSERT INTO bk_cajero_role_sections (role_id, section)
  SELECT rs.role_id, rs.section FROM role_sections rs JOIN roles r ON r.id = rs.role_id
   WHERE r.name IN ('MESERO', 'REPARTIDOR') AND rs.section = 'CASH_CUTS'
  ON CONFLICT DO NOTHING;

  DELETE FROM role_sections rs USING roles r
   WHERE r.id = rs.role_id AND r.name IN ('MESERO', 'REPARTIDOR') AND rs.section = 'CASH_CUTS';
  GET DIAGNOSTICS n = ROW_COUNT;
  RAISE NOTICE 'CASH_CUTS quitado a % rol(es) MESERO/REPARTIDOR', n;
END $$;
