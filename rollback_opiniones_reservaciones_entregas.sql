-- =====================================================================
--  Rollback de migration_opiniones_reservaciones_entregas.sql
--
--  ⚠️ BORRA DATOS: elimina todas las opiniones, reservaciones e histórico de entregas, los
--  códigos QR de opinión de las cuentas, los comprobantes de transferencia registrados en las
--  órdenes (las fotos en disco no se tocan) y la configuración de reservaciones de cada tienda.
--  Respalda la base antes de correrlo (pg_dump).
--
--  Correrlo con el backend DETENIDO y con el código anterior a estos cambios: si el backend
--  nuevo arranca con ddl-auto=update, Hibernate vuelve a crear todo.
--
--  PostgreSQL. Idempotente: se puede correr más de una vez.
-- =====================================================================

BEGIN;

-- Tablas nuevas (primero las que dependen de otras).
DROP TABLE IF EXISTS opinion_platillos;
DROP TABLE IF EXISTS opiniones;
DROP TABLE IF EXISTS reservaciones;
DROP TABLE IF EXISTS entrega_eventos;

-- Columnas nuevas (al borrar la columna se borra también su restricción UNIQUE).
ALTER TABLE cuentas DROP COLUMN IF EXISTS opinion_token;

ALTER TABLE ordenes DROP COLUMN IF EXISTS tomada_at;
ALTER TABLE ordenes DROP COLUMN IF EXISTS comprobante_path;
ALTER TABLE ordenes DROP COLUMN IF EXISTS comprobante_at;
ALTER TABLE ordenes DROP COLUMN IF EXISTS transferencia_incluye_envio;

ALTER TABLE tienda_info DROP COLUMN IF EXISTS web_reservas_enabled;
ALTER TABLE tienda_info DROP COLUMN IF EXISTS reserva_max_personas;
ALTER TABLE tienda_info DROP COLUMN IF EXISTS reserva_anticipacion_horas;
ALTER TABLE tienda_info DROP COLUMN IF EXISTS reserva_dias_max;
ALTER TABLE tienda_info DROP COLUMN IF EXISTS reserva_hora_inicio;
ALTER TABLE tienda_info DROP COLUMN IF EXISTS reserva_hora_fin;

COMMIT;
