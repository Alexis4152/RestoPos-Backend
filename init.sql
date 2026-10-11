-- ============================================================
--  RestoPOS — PostgreSQL schema
--  DB: restopos_db  |  host: localhost
--  Run: psql -U postgres -d restopos_db -f init.sql
-- ============================================================

-- Tiendas dadas de alta en la plataforma (módulo /api/tiendas)
CREATE TABLE IF NOT EXISTS tiendas (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(150) NOT NULL,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    primary_color       VARCHAR(7),
    logo_path           VARCHAR(255),
    deleted_at          TIMESTAMP,
    updated_at          TIMESTAMP,
    -- sin FK aquí: sería circular con "users" (users.tienda_id -> tiendas, y estas tres
    -- columnas -> users). Hibernate agrega las 3 foreign keys al arrancar la app, igual
    -- que con users.role_id más abajo.
    created_by_user_id  BIGINT,
    updated_by_user_id  BIGINT,
    deleted_by_user_id  BIGINT,
    -- usuario de plataforma con rol SUPERVISOR a cargo de esta tienda (null = sin
    -- supervisor asignado); sin FK aquí por la misma razón circular que las 3 columnas
    -- de arriba, Hibernate la agrega al arrancar la app.
    supervisor_id       BIGINT
);

CREATE TABLE IF NOT EXISTS users (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(100) NOT NULL,
    email               VARCHAR(150) NOT NULL UNIQUE,
    password            VARCHAR(255) NOT NULL,
    tienda_id           BIGINT REFERENCES tiendas(id),
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMP,
    created_by_user_id  BIGINT REFERENCES users(id),
    updated_by_user_id  BIGINT REFERENCES users(id),
    deleted_by_user_id  BIGINT REFERENCES users(id),
    -- sin FK aquí porque "roles" se crea más abajo; Hibernate agrega
    -- users.role_id -> roles(id) al arrancar la app.
    role_id             BIGINT
);

-- Refresh tokens de sesión (string opaco, no JWT) — 8h de vigencia, viajan como cookie
-- httpOnly (ver AuthController/AuthService). Revocables de verdad (a diferencia del access
-- token JWT, que solo se valida contra su firma) vía la columna "revoked".
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id          BIGSERIAL PRIMARY KEY,
    token       VARCHAR(64) NOT NULL UNIQUE,
    user_id     BIGINT NOT NULL REFERENCES users(id),
    expires_at  TIMESTAMP NOT NULL,
    revoked     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Roles por tienda (RBAC) — cada tienda tiene su propio ADMIN/MESERO independiente, salvo
-- SUPER_ADMIN, que es global (tienda_id NULL). El sembrado inicial lo hacen
-- RoleDataInitializer + TenantDataInitializer al arrancar la app.
CREATE TABLE IF NOT EXISTS roles (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(40) NOT NULL,
    description         VARCHAR(200),
    is_system           BOOLEAN NOT NULL,
    tienda_id           BIGINT REFERENCES tiendas(id),
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP,
    updated_at          TIMESTAMP,
    deleted_at          TIMESTAMP,
    created_by_user_id  BIGINT REFERENCES users(id),
    updated_by_user_id  BIGINT REFERENCES users(id),
    deleted_by_user_id  BIGINT REFERENCES users(id),
    UNIQUE (tienda_id, name)
);

-- Secciones (AppSection) habilitadas por cada rol — controla qué módulos ve cada usuario.
CREATE TABLE IF NOT EXISTS role_sections (
    role_id     BIGINT NOT NULL REFERENCES roles(id),
    section     VARCHAR(20) NOT NULL
                    CHECK (section IN ('DASHBOARD','MENU','TABLES','ORDERS','CASH_CUTS',
                                        'REPORTS','USERS','ROLES','SALES')),
    PRIMARY KEY (role_id, section)
);

CREATE TABLE IF NOT EXISTS categories (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(100) NOT NULL,
    description         TEXT,
    tienda_id           BIGINT REFERENCES tiendas(id),
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP,
    deleted_at          TIMESTAMP,
    created_by_user_id  BIGINT REFERENCES users(id),
    updated_by_user_id  BIGINT REFERENCES users(id),
    deleted_by_user_id  BIGINT REFERENCES users(id)
);

-- Productos del menú (platillos, bebidas, etc.). stock/min_stock/cost/unit/barcode quedan
-- disponibles para un futuro módulo de inventario/insumos, pero no son obligatorios para
-- operar el menú de un restaurante.
CREATE TABLE IF NOT EXISTS products (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(200) NOT NULL,
    description         TEXT,
    barcode             VARCHAR(100),
    price               NUMERIC(12,2) NOT NULL DEFAULT 0,
    cost                NUMERIC(12,2) NOT NULL DEFAULT 0,
    stock               INTEGER NOT NULL DEFAULT 0,
    min_stock           INTEGER NOT NULL DEFAULT 0,
    unit                VARCHAR(20) NOT NULL DEFAULT 'pza',
    image_url           VARCHAR(255),
    disponible          BOOLEAN NOT NULL DEFAULT TRUE,
    category_id         BIGINT REFERENCES categories(id) ON DELETE SET NULL,
    tienda_id           BIGINT REFERENCES tiendas(id),
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMP,
    created_by_user_id  BIGINT REFERENCES users(id),
    updated_by_user_id  BIGINT REFERENCES users(id),
    deleted_by_user_id  BIGINT REFERENCES users(id)
);

-- Mesas del restaurante. orden_activa_id se agrega como FK circular por Hibernate al
-- arrancar (mesas <-> ordenes se referencian mutuamente).
CREATE TABLE IF NOT EXISTS mesas (
    id                  BIGSERIAL PRIMARY KEY,
    numero              VARCHAR(20) NOT NULL,
    capacidad           INTEGER,
    estado              VARCHAR(20) NOT NULL DEFAULT 'DISPONIBLE'
                            CHECK (estado IN ('DISPONIBLE','OCUPADA','RESERVADA','PENDIENTE_PAGO','FUERA_DE_SERVICIO')),
    orden_activa_id     BIGINT,
    tienda_id           BIGINT REFERENCES tiendas(id),
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP,
    deleted_at          TIMESTAMP,
    created_by_user_id  BIGINT REFERENCES users(id),
    updated_by_user_id  BIGINT REFERENCES users(id),
    deleted_by_user_id  BIGINT REFERENCES users(id)
);

-- Comandas. mesa_id es NULL para órdenes para llevar/sin mesa.
CREATE TABLE IF NOT EXISTS ordenes (
    id                      BIGSERIAL PRIMARY KEY,
    tienda_id               BIGINT REFERENCES tiendas(id),
    mesa_id                 BIGINT REFERENCES mesas(id),
    mesero_id               BIGINT NOT NULL REFERENCES users(id),
    numero_comensales       INTEGER NOT NULL DEFAULT 1,
    estado                  VARCHAR(20) NOT NULL DEFAULT 'ABIERTA'
                                CHECK (estado IN ('ABIERTA','EN_PROCESO','LISTA','PENDIENTE_PAGO','PAGADA','CANCELADA')),
    notas                   TEXT,
    subtotal                NUMERIC(12,2) NOT NULL DEFAULT 0,
    descuento               NUMERIC(12,2) NOT NULL DEFAULT 0,
    impuesto                NUMERIC(12,2) NOT NULL DEFAULT 0,
    total                   NUMERIC(12,2) NOT NULL DEFAULT 0,
    opened_at               TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP,
    closed_at               TIMESTAMP,
    cancelled_by_user_id    BIGINT REFERENCES users(id),
    cancelled_at            TIMESTAMP,
    motivo_cancelacion      VARCHAR(255)
);

-- mesas.orden_activa_id -> ordenes(id) es circular (ordenes.mesa_id -> mesas también);
-- igual que con tiendas/users más arriba, Hibernate agrega esta FK al arrancar la app.

CREATE TABLE IF NOT EXISTS comensales (
    id          BIGSERIAL PRIMARY KEY,
    orden_id    BIGINT NOT NULL REFERENCES ordenes(id),
    numero      INTEGER NOT NULL,
    nombre      VARCHAR(100),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Líneas de una orden. cantidad_facturada + version (bloqueo optimista) garantizan que
-- nunca se cobre dos veces la misma unidad de un producto.
CREATE TABLE IF NOT EXISTS orden_detalles (
    id                  BIGSERIAL PRIMARY KEY,
    orden_id            BIGINT NOT NULL REFERENCES ordenes(id) ON DELETE CASCADE,
    comensal_id         BIGINT REFERENCES comensales(id),
    producto_id         BIGINT REFERENCES products(id) ON DELETE SET NULL,
    producto_nombre     VARCHAR(200) NOT NULL,
    cantidad            NUMERIC(10,3) NOT NULL DEFAULT 1,
    precio_unitario     NUMERIC(12,2) NOT NULL,
    descuento           NUMERIC(12,2) NOT NULL DEFAULT 0,
    subtotal            NUMERIC(12,2) NOT NULL,
    observaciones       TEXT,
    estado              VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE'
                            CHECK (estado IN ('PENDIENTE','PREPARANDO','LISTO','ENTREGADO_MESERO','ENTREGADO','CANCELADO')),
    cantidad_facturada  NUMERIC(10,3) NOT NULL DEFAULT 0,
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Cuentas a cobrar (total / por comensal / personalizada) de una orden.
CREATE TABLE IF NOT EXISTS cuentas (
    id                      BIGSERIAL PRIMARY KEY,
    orden_id                BIGINT NOT NULL REFERENCES ordenes(id),
    tipo                    VARCHAR(15) NOT NULL CHECK (tipo IN ('TOTAL','POR_COMENSAL','PERSONALIZADA')),
    comensal_id             BIGINT REFERENCES comensales(id),
    total                   NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_pagado            NUMERIC(12,2) NOT NULL DEFAULT 0,
    estado                  VARCHAR(15) NOT NULL DEFAULT 'PENDIENTE' CHECK (estado IN ('PENDIENTE','PAGADA','CANCELADA')),
    ticket_folio            VARCHAR(30),
    ticket_email_enviado    BOOLEAN NOT NULL DEFAULT FALSE,
    ticket_email_destino    VARCHAR(150),
    created_by_user_id      BIGINT REFERENCES users(id),
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    closed_at               TIMESTAMP
);

CREATE TABLE IF NOT EXISTS cuenta_detalles (
    id                  BIGSERIAL PRIMARY KEY,
    cuenta_id           BIGINT NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE,
    orden_detalle_id    BIGINT NOT NULL REFERENCES orden_detalles(id),
    cantidad            NUMERIC(10,3) NOT NULL,
    monto               NUMERIC(12,2) NOT NULL
);

CREATE TABLE IF NOT EXISTS cash_cuts (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id),
    tienda_id           BIGINT REFERENCES tiendas(id),
    opening_amount      NUMERIC(12,2) NOT NULL DEFAULT 0,
    closing_amount      NUMERIC(12,2),
    expenses            NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_sales         NUMERIC(12,2) NOT NULL DEFAULT 0,
    cash_sales          NUMERIC(12,2) NOT NULL DEFAULT 0,
    card_sales          NUMERIC(12,2) NOT NULL DEFAULT 0,
    transfer_sales      NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_transactions  INTEGER NOT NULL DEFAULT 0,
    cancelled_count     INTEGER NOT NULL DEFAULT 0,
    cancelled_total     NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_entradas      NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_salidas       NUMERIC(12,2) NOT NULL DEFAULT 0,
    status              VARCHAR(10) NOT NULL DEFAULT 'OPEN'
                            CHECK (status IN ('OPEN','CLOSED')),
    notes               TEXT,
    opened_at           TIMESTAMP NOT NULL DEFAULT NOW(),
    closed_at           TIMESTAMP,
    -- NULL si sigue abierto, o si lo cerró CashCutAutoCloseJob (el sistema) en vez de una persona.
    closed_by_user_id   BIGINT REFERENCES users(id)
);

-- Pagos (fuente de verdad del dinero cobrado; un pago mixto son varias filas sobre la misma cuenta).
CREATE TABLE IF NOT EXISTS pagos (
    id                      BIGSERIAL PRIMARY KEY,
    cuenta_id               BIGINT NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE,
    metodo                  VARCHAR(20) NOT NULL CHECK (metodo IN ('CASH','CARD','TRANSFER')),
    monto                   NUMERIC(12,2) NOT NULL,
    recibido                NUMERIC(12,2),
    referencia              VARCHAR(100),
    cash_cut_id             BIGINT NOT NULL REFERENCES cash_cuts(id),
    usuario_id              BIGINT NOT NULL REFERENCES users(id),
    cancelado               BOOLEAN NOT NULL DEFAULT FALSE,
    cancelled_by_user_id    BIGINT REFERENCES users(id),
    cancelled_at            TIMESTAMP,
    motivo_cancelacion      VARCHAR(255),
    created_at              TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Entradas/salidas/ajustes manuales de efectivo en un corte (las ventas ya quedan en "pagos").
CREATE TABLE IF NOT EXISTS movimientos_caja (
    id          BIGSERIAL PRIMARY KEY,
    cash_cut_id BIGINT NOT NULL REFERENCES cash_cuts(id),
    tipo        VARCHAR(10) NOT NULL CHECK (tipo IN ('ENTRADA','SALIDA','AJUSTE')),
    monto       NUMERIC(12,2) NOT NULL,
    motivo      VARCHAR(255) NOT NULL,
    usuario_id  BIGINT NOT NULL REFERENCES users(id),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Bitácora de auditoría de operaciones sensibles (cancelaciones, caja, pagos, etc.).
CREATE TABLE IF NOT EXISTS auditoria (
    id              BIGSERIAL PRIMARY KEY,
    usuario_id      BIGINT REFERENCES users(id),
    tienda_id       BIGINT REFERENCES tiendas(id),
    accion          VARCHAR(60) NOT NULL,
    entidad         VARCHAR(60) NOT NULL,
    entidad_id      BIGINT,
    valor_anterior  TEXT,
    valor_nuevo     TEXT,
    motivo          TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS inventory_movements (
    id              BIGSERIAL PRIMARY KEY,
    product_id      BIGINT NOT NULL REFERENCES products(id),
    user_id         BIGINT NOT NULL REFERENCES users(id),
    type            VARCHAR(15) NOT NULL
                        CHECK (type IN ('IN','OUT','ADJUSTMENT','SALE')),
    quantity        INTEGER NOT NULL,
    previous_stock  INTEGER NOT NULL,
    new_stock       INTEGER NOT NULL,
    reason          VARCHAR(255),
    reference       VARCHAR(100),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Datos fiscales/de contacto de cada tienda, usados en el ticket PDF (dirección separada en
-- 5 campos: calle, colonia, código postal, localidad, estado — nunca combinados en un texto
-- libre, para poder imprimir cada uno en su propia línea).
CREATE TABLE IF NOT EXISTS tienda_info (
    id                  BIGSERIAL PRIMARY KEY,
    tienda_id           BIGINT NOT NULL UNIQUE REFERENCES tiendas(id),
    rfc                 VARCHAR(20),
    razon_social        VARCHAR(200),
    telefono            VARCHAR(30),
    pagina_web          VARCHAR(200),
    redes_sociales      TEXT,
    calle               VARCHAR(200),
    colonia             VARCHAR(150),
    codigo_postal       VARCHAR(10),
    localidad           VARCHAR(150),
    estado              VARCHAR(100),
    notas_adicionales   TEXT,
    created_at          TIMESTAMP,
    updated_at          TIMESTAMP,
    created_by_user_id  BIGINT REFERENCES users(id),
    updated_by_user_id  BIGINT REFERENCES users(id)
);

-- Fila única (id=1) con la hora global en que corre el cierre automático de cortes de caja.
CREATE TABLE IF NOT EXISTS cash_cut_schedule (
    id                  BIGINT PRIMARY KEY,
    close_hour          INTEGER NOT NULL,
    close_minute        INTEGER NOT NULL,
    enabled             BOOLEAN NOT NULL,
    updated_at          TIMESTAMP,
    updated_by_user_id  BIGINT REFERENCES users(id)
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_products_category   ON products(category_id);
CREATE INDEX IF NOT EXISTS idx_products_barcode    ON products(barcode);
CREATE INDEX IF NOT EXISTS idx_products_tienda     ON products(tienda_id);
CREATE INDEX IF NOT EXISTS idx_categories_tienda   ON categories(tienda_id);
CREATE INDEX IF NOT EXISTS idx_mesas_tienda        ON mesas(tienda_id);
CREATE INDEX IF NOT EXISTS idx_ordenes_tienda      ON ordenes(tienda_id);
CREATE INDEX IF NOT EXISTS idx_ordenes_mesa        ON ordenes(mesa_id);
CREATE INDEX IF NOT EXISTS idx_ordenes_opened_at   ON ordenes(opened_at);
CREATE INDEX IF NOT EXISTS idx_orden_detalles_orden ON orden_detalles(orden_id);
CREATE INDEX IF NOT EXISTS idx_cuentas_orden       ON cuentas(orden_id);
CREATE INDEX IF NOT EXISTS idx_cuenta_detalles_cuenta ON cuenta_detalles(cuenta_id);
CREATE INDEX IF NOT EXISTS idx_pagos_cuenta        ON pagos(cuenta_id);
CREATE INDEX IF NOT EXISTS idx_pagos_cash_cut      ON pagos(cash_cut_id);
CREATE INDEX IF NOT EXISTS idx_pagos_created_at    ON pagos(created_at);
CREATE INDEX IF NOT EXISTS idx_movimientos_cash_cut ON movimientos_caja(cash_cut_id);
CREATE INDEX IF NOT EXISTS idx_auditoria_tienda    ON auditoria(tienda_id);
CREATE INDEX IF NOT EXISTS idx_inv_movements_prod  ON inventory_movements(product_id);
CREATE INDEX IF NOT EXISTS idx_cash_cuts_status    ON cash_cuts(status);
CREATE INDEX IF NOT EXISTS idx_cash_cuts_tienda    ON cash_cuts(tienda_id);
CREATE INDEX IF NOT EXISTS idx_users_tienda        ON users(tienda_id);

-- Tienda por defecto para el primer arranque
INSERT INTO tiendas (name)
SELECT 'Restaurante Principal'
WHERE NOT EXISTS (SELECT 1 FROM tiendas LIMIT 1);

-- Default admin (password: admin123). El rol (role_id) se lo asignan RoleDataInitializer +
-- TenantDataInitializer al arrancar la app: siembran SUPER_ADMIN/ADMIN/MESERO y le dan el
-- ADMIN de su tienda a este usuario. La tabla roles la crea este script (vacía); el
-- contenido y la FK users.role_id los administra Hibernate/la app al arrancar.
INSERT INTO users (name, email, password, tienda_id)
SELECT 'Administrador','admin@restopos.com',
       '$2b$10$PMm3XPaFv7Rm150MI4NP2uFHtyQ6Sxh1UDBGwcaSp9v7Cn3Ikn/ou',
       (SELECT id FROM tiendas ORDER BY id LIMIT 1)
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email='admin@restopos.com');

-- Default categories (menú de restaurante)
INSERT INTO categories (name, description, tienda_id)
SELECT v.n, v.d, (SELECT id FROM tiendas ORDER BY id LIMIT 1)
FROM (VALUES
  ('Entradas',  'Platillos de entrada'),
  ('Platillos', 'Platos fuertes'),
  ('Bebidas',   'Bebidas frías y calientes'),
  ('Postres',   'Postres y repostería'),
  ('Extras',    'Complementos y extras')
) AS v(n,d)
WHERE NOT EXISTS (SELECT 1 FROM categories LIMIT 1);

-- Mesas de ejemplo
INSERT INTO mesas (numero, capacidad, tienda_id)
SELECT v.n, v.c, (SELECT id FROM tiendas ORDER BY id LIMIT 1)
FROM (VALUES ('1',4), ('2',4), ('3',2), ('4',6), ('5',2)) AS v(n,c)
WHERE NOT EXISTS (SELECT 1 FROM mesas LIMIT 1);
