-- =====================================================================
--  Migración: dirección de entrega en campos separados
--
--  Columnas nuevas (opcionales) en ordenes y clientes:
--    calle (varchar 100)  "calle y número"
--    colonia (varchar 70)
--    codigo_postal (varchar 10)
--
--  La columna "direccion" se queda: guarda la dirección completa armada
--  ("Av. Juárez 123, Col. Centro, C.P. 06000"), que es la que usan el repartidor, el mapa,
--  los avisos y los tickets. Los pedidos y clientes anteriores conservan su dirección en un
--  solo texto (las partes quedan en NULL); el cliente la pasa a los campos desde "Mis datos".
--
--  PostgreSQL. UNA SOLA SENTENCIA (bloque DO), se puede correr más de una vez.
--  Deshacer: rollback_direccion_partes.sql
-- =====================================================================
DO $$
BEGIN
  ALTER TABLE ordenes ADD COLUMN IF NOT EXISTS calle varchar(100);
  ALTER TABLE ordenes ADD COLUMN IF NOT EXISTS colonia varchar(70);
  ALTER TABLE ordenes ADD COLUMN IF NOT EXISTS codigo_postal varchar(10);
  ALTER TABLE clientes ADD COLUMN IF NOT EXISTS calle varchar(100);
  ALTER TABLE clientes ADD COLUMN IF NOT EXISTS colonia varchar(70);
  ALTER TABLE clientes ADD COLUMN IF NOT EXISTS codigo_postal varchar(10);
  RAISE NOTICE 'Dirección en partes lista (ordenes y clientes)';
END $$;
