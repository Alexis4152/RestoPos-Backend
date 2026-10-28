-- =====================================================================
--  Migración: tope de propina configurable por tienda
--
--  Columna nueva: tiendas.propina_max_porcentaje (entero 1–100, default 50)
--
--  El cajero no puede registrar una propina mayor a ese % del consumo (ni en porcentaje ni
--  en monto fijo). Lo cambia el admin o supervisor en "Datos de la tienda". Las tiendas
--  existentes quedan en 50%, que es el tope que ya había.
--
--  PostgreSQL. UNA SOLA SENTENCIA (bloque DO), se puede correr más de una vez (si Hibernate
--  ya creó la columna con ddl-auto=update, solo se asegura el default y la restricción).
--  Deshacer: rollback_propina_tope.sql
-- =====================================================================
DO $$
BEGIN
  ALTER TABLE tiendas ADD COLUMN IF NOT EXISTS propina_max_porcentaje integer;
  UPDATE tiendas SET propina_max_porcentaje = 50
   WHERE propina_max_porcentaje IS NULL OR propina_max_porcentaje NOT BETWEEN 1 AND 100;
  ALTER TABLE tiendas ALTER COLUMN propina_max_porcentaje SET DEFAULT 50;
  ALTER TABLE tiendas ALTER COLUMN propina_max_porcentaje SET NOT NULL;

  ALTER TABLE tiendas DROP CONSTRAINT IF EXISTS tiendas_propina_max_porcentaje_check;
  ALTER TABLE tiendas ADD CONSTRAINT tiendas_propina_max_porcentaje_check
    CHECK (propina_max_porcentaje BETWEEN 1 AND 100);

  RAISE NOTICE 'Tope de propina listo (tiendas en 50%% salvo las ya configuradas)';
END $$;
