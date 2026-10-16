-- =====================================================================
--  Migración: opiniones de mesa, reservaciones, comprobante de transferencia
--             e histórico de entregas a domicilio
--
--  Tablas nuevas:
--    opiniones / opinion_platillos  Opinión del cliente por cuenta de mesa (QR del ticket o
--                                   "Mis pedidos"): estrellas, mesero y 👍/👎 por platillo.
--    reservaciones                  Reservaciones de mesa desde la página web o capturadas por
--                                   el personal, con necesidades especiales (periqueras, mesa
--                                   accesible, carriola, ocasión).
--    entrega_eventos                Histórico del envío a domicilio: tomado, soltado, en camino,
--                                   entregado (quién y cuándo).
--
--  Columnas nuevas:
--    cuentas.opinion_token          Código del QR "califica tu visita" del ticket.
--    ordenes.tomada_at              Cuándo tomó el envío el repartidor actual.
--    ordenes.comprobante_path       Foto del comprobante de transferencia (pedido web).
--    ordenes.comprobante_at         Cuándo la subió el cliente.
--    ordenes.transferencia_incluye_envio  Si la transferencia incluye el costo de envío.
--    tienda_info.web_reservas_enabled, reserva_max_personas, reserva_anticipacion_horas,
--    reserva_dias_max, reserva_hora_inicio, reserva_hora_fin   Configuración de reservaciones.
--
--  PostgreSQL. Idempotente: se puede correr más de una vez (si Hibernate ya creó algo con
--  ddl-auto=update, lo que ya existe se deja como está).
--  Deshacer: rollback_opiniones_reservaciones_entregas.sql
-- =====================================================================

BEGIN;

-- ── Columnas nuevas en tablas existentes ─────────────────────────────

-- La restricción UNIQUE solo se agrega si la columna es nueva (si ya existía, Hibernate ya
-- le puso la suya y no se duplica).
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'cuentas' AND column_name = 'opinion_token') THEN
        ALTER TABLE cuentas ADD COLUMN opinion_token VARCHAR(64);
        ALTER TABLE cuentas ADD CONSTRAINT uk_cuentas_opinion_token UNIQUE (opinion_token);
    END IF;
END $$;

ALTER TABLE ordenes ADD COLUMN IF NOT EXISTS tomada_at TIMESTAMP(6);
ALTER TABLE ordenes ADD COLUMN IF NOT EXISTS comprobante_path VARCHAR(255);
ALTER TABLE ordenes ADD COLUMN IF NOT EXISTS comprobante_at TIMESTAMP(6);
ALTER TABLE ordenes ADD COLUMN IF NOT EXISTS transferencia_incluye_envio BOOLEAN;

ALTER TABLE tienda_info ADD COLUMN IF NOT EXISTS web_reservas_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tienda_info ADD COLUMN IF NOT EXISTS reserva_max_personas INTEGER NOT NULL DEFAULT 12;
ALTER TABLE tienda_info ADD COLUMN IF NOT EXISTS reserva_anticipacion_horas INTEGER NOT NULL DEFAULT 2;
ALTER TABLE tienda_info ADD COLUMN IF NOT EXISTS reserva_dias_max INTEGER NOT NULL DEFAULT 30;
ALTER TABLE tienda_info ADD COLUMN IF NOT EXISTS reserva_hora_inicio TIME;
ALTER TABLE tienda_info ADD COLUMN IF NOT EXISTS reserva_hora_fin TIME;

-- ── Opiniones de mesa ────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS opiniones (
    id               BIGSERIAL PRIMARY KEY,
    tienda_id        BIGINT REFERENCES tiendas(id),
    cuenta_id        BIGINT NOT NULL REFERENCES cuentas(id),
    orden_id         BIGINT NOT NULL REFERENCES ordenes(id),
    mesero_id        BIGINT REFERENCES users(id),
    cliente_id       BIGINT REFERENCES clientes(id),
    canal            VARCHAR(10) NOT NULL,          -- QR o CUENTA
    estrellas        INTEGER NOT NULL,
    mesero_estrellas INTEGER,
    comentario       TEXT,
    created_at       TIMESTAMP(6),
    CONSTRAINT uk_opiniones_cuenta UNIQUE (cuenta_id)  -- una opinión por cuenta
);

CREATE TABLE IF NOT EXISTS opinion_platillos (
    id               BIGSERIAL PRIMARY KEY,
    opinion_id       BIGINT NOT NULL REFERENCES opiniones(id),
    orden_detalle_id BIGINT NOT NULL,
    producto_nombre  VARCHAR(200) NOT NULL,
    me_gusta         BOOLEAN NOT NULL
);

-- ── Reservaciones ────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS reservaciones (
    id              BIGSERIAL PRIMARY KEY,
    version         BIGINT NOT NULL DEFAULT 0,
    tienda_id       BIGINT NOT NULL REFERENCES tiendas(id),
    cliente_id      BIGINT REFERENCES clientes(id),
    nombre          VARCHAR(120) NOT NULL,
    telefono        VARCHAR(30) NOT NULL,
    email           VARCHAR(150),
    fecha_hora      TIMESTAMP(6) NOT NULL,
    personas        INTEGER NOT NULL,
    zona_id         BIGINT REFERENCES zonas(id),       -- zona preferida
    mesa_id         BIGINT REFERENCES mesas(id),       -- mesa asignada por el personal
    periqueras      INTEGER NOT NULL DEFAULT 0,
    accesible       BOOLEAN NOT NULL DEFAULT FALSE,    -- silla de ruedas / movilidad reducida
    carriola        BOOLEAN NOT NULL DEFAULT FALSE,
    ocasion         VARCHAR(60),
    notas           VARCHAR(500),
    estado          VARCHAR(15) NOT NULL,
    motivo          VARCHAR(300),
    origen          VARCHAR(10) NOT NULL,              -- WEB o PERSONAL
    token           VARCHAR(64) NOT NULL,
    atendida_por_id BIGINT REFERENCES users(id),
    created_at      TIMESTAMP(6),
    updated_at      TIMESTAMP(6),
    CONSTRAINT uk_reservaciones_token UNIQUE (token),
    CONSTRAINT reservaciones_estado_check
        CHECK (estado IN ('PENDIENTE','CONFIRMADA','RECHAZADA','CANCELADA','LLEGO','NO_LLEGO'))
);

CREATE INDEX IF NOT EXISTS idx_reservaciones_tienda_fecha ON reservaciones (tienda_id, fecha_hora);

-- ── Histórico de entregas a domicilio ────────────────────────────────

CREATE TABLE IF NOT EXISTS entrega_eventos (
    id            BIGSERIAL PRIMARY KEY,
    orden_id      BIGINT NOT NULL,
    tipo          VARCHAR(15) NOT NULL,              -- TOMADO, SOLTADO, EN_CAMINO, ENTREGADO
    repartidor_id BIGINT REFERENCES users(id),
    actor_id      BIGINT REFERENCES users(id),       -- quién hizo el movimiento
    detalle       VARCHAR(100),
    created_at    TIMESTAMP(6)
);

CREATE INDEX IF NOT EXISTS idx_entrega_eventos_orden ON entrega_eventos (orden_id);

COMMIT;
