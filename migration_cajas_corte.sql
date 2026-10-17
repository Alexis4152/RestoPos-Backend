-- =====================================================================
--  Migración: nuevo corte de caja (traído de DemoPV)
--
--  Cajas físicas, recepción/entrega de caja entre turnos, arqueo al cerrar con resolución de
--  diferencias, movimientos de efectivo (gastos, retiros, aportes con reembolso, cambio,
--  pagos de faltantes, préstamos entre cajas), faltantes con abonos, corte de fin de día por
--  caja y destinatarios del reporte.
--
--  Tablas nuevas:  cash_registers, cash_movements, reimbursements, shortages,
--                  shortage_payments, cash_report_recipients
--  Columnas nuevas: tiendas.blind_cash_count, tiendas.report_to_supervisor y varias en
--                   cash_cuts (caja, recepción, entrega, arqueo, totales de movimientos)
--
--  Reemplaza a "movimientos_caja" (entradas/salidas/ajustes manuales de RestoPOS): sus
--  registros se copian a cash_movements (ENTRADA → FLOAT, SALIDA → EXPENSE, AJUSTE según su
--  signo). La tabla movimientos_caja NO se borra (el rollback la necesita).
--
--  Las tiendas no necesitan una caja dada de alta: la primera vez que alguien consulta las
--  cajas de una tienda sin ninguna, el backend crea "Caja 1". Los cortes anteriores quedan con
--  cash_register_id en NULL y siguen funcionando (sin entrega de turno).
--
--  PostgreSQL. Idempotente: se puede correr más de una vez (si Hibernate ya creó algo con
--  ddl-auto=update, lo que ya existe se deja como está).
--  Deshacer: rollback_cajas_corte.sql
-- =====================================================================

BEGIN;

-- ── Tienda ──────────────────────────────────────────────────────────
-- Conteo ciego al cerrar corte (opcional por tienda, "Datos de la tienda").
ALTER TABLE tiendas ADD COLUMN IF NOT EXISTS blind_cash_count BOOLEAN NOT NULL DEFAULT FALSE;
-- El supervisor (dueño) recibe el reporte de fin de día por default.
ALTER TABLE tiendas ADD COLUMN IF NOT EXISTS report_to_supervisor BOOLEAN NOT NULL DEFAULT TRUE;

-- ── Cajas ───────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS cash_registers (
    id                          BIGSERIAL PRIMARY KEY,
    tienda_id                   BIGINT NOT NULL REFERENCES tiendas(id),
    name                        VARCHAR(60) NOT NULL,
    is_active                   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at                  TIMESTAMP(6),
    -- entrega pendiente mientras la caja está cerrada (NULL si hay corte abierto)
    pending_amount              NUMERIC(12,2),
    pending_count_method        VARCHAR(20),
    pending_counted_by_user_id  BIGINT REFERENCES users(id),
    pending_counted_at          TIMESTAMP(6),
    last_cut_id                 BIGINT REFERENCES cash_cuts(id),
    -- corte de fin de día
    day_closed                  BOOLEAN NOT NULL DEFAULT FALSE,
    last_day_close_at           TIMESTAMP(6)
);
ALTER TABLE cash_registers ADD COLUMN IF NOT EXISTS day_closed        BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE cash_registers ADD COLUMN IF NOT EXISTS last_day_close_at TIMESTAMP(6);
CREATE INDEX IF NOT EXISTS idx_cash_registers_tienda ON cash_registers(tienda_id);

-- ── Corte: caja, recepción, entrega, arqueo y totales ───────────────
ALTER TABLE cash_cuts ADD COLUMN IF NOT EXISTS cash_register_id             BIGINT REFERENCES cash_registers(id);
ALTER TABLE cash_cuts ADD COLUMN IF NOT EXISTS previous_cut_id              BIGINT REFERENCES cash_cuts(id);
ALTER TABLE cash_cuts ADD COLUMN IF NOT EXISTS expected_opening_amount      NUMERIC(12,2);
ALTER TABLE cash_cuts ADD COLUMN IF NOT EXISTS received_count_method        VARCHAR(20);
ALTER TABLE cash_cuts ADD COLUMN IF NOT EXISTS receive_note                 TEXT;
ALTER TABLE cash_cuts ADD COLUMN IF NOT EXISTS handover_status              VARCHAR(12) NOT NULL DEFAULT 'NONE';
ALTER TABLE cash_cuts ADD COLUMN IF NOT EXISTS handover_difference          NUMERIC(12,2);
ALTER TABLE cash_cuts ADD COLUMN IF NOT EXISTS handover_resolution          VARCHAR(25);
ALTER TABLE cash_cuts ADD COLUMN IF NOT EXISTS handover_resolved_by_user_id BIGINT REFERENCES users(id);
ALTER TABLE cash_cuts ADD COLUMN IF NOT EXISTS handover_resolved_at         TIMESTAMP(6);
ALTER TABLE cash_cuts ADD COLUMN IF NOT EXISTS handover_resolution_note     TEXT;
ALTER TABLE cash_cuts ADD COLUMN IF NOT EXISTS entries_total                NUMERIC(12,2);
ALTER TABLE cash_cuts ADD COLUMN IF NOT EXISTS withdrawals_total            NUMERIC(12,2);
ALTER TABLE cash_cuts ADD COLUMN IF NOT EXISTS reimbursements_total         NUMERIC(12,2);
ALTER TABLE cash_cuts ADD COLUMN IF NOT EXISTS loans_out_total              NUMERIC(12,2);
ALTER TABLE cash_cuts ADD COLUMN IF NOT EXISTS counted_amount               NUMERIC(12,2);
ALTER TABLE cash_cuts ADD COLUMN IF NOT EXISTS cash_difference              NUMERIC(12,2);
ALTER TABLE cash_cuts ADD COLUMN IF NOT EXISTS count_method                 VARCHAR(20);
ALTER TABLE cash_cuts ADD COLUMN IF NOT EXISTS counted_by_user_id           BIGINT REFERENCES users(id);
ALTER TABLE cash_cuts ADD COLUMN IF NOT EXISTS count_evidence_path          VARCHAR(300);
ALTER TABLE cash_cuts ADD COLUMN IF NOT EXISTS handover_amount              NUMERIC(12,2);
ALTER TABLE cash_cuts ADD COLUMN IF NOT EXISTS arqueo_status                VARCHAR(12) NOT NULL DEFAULT 'NONE';
ALTER TABLE cash_cuts ADD COLUMN IF NOT EXISTS arqueo_resolution            VARCHAR(15);
ALTER TABLE cash_cuts ADD COLUMN IF NOT EXISTS arqueo_resolved_by_user_id   BIGINT REFERENCES users(id);
ALTER TABLE cash_cuts ADD COLUMN IF NOT EXISTS arqueo_resolved_at           TIMESTAMP(6);
ALTER TABLE cash_cuts ADD COLUMN IF NOT EXISTS arqueo_resolution_note       TEXT;
CREATE INDEX IF NOT EXISTS idx_cash_cuts_register ON cash_cuts(cash_register_id);

-- ── Reembolsos (aportes de una persona que la tienda le debe regresar) ──
CREATE TABLE IF NOT EXISTS reimbursements (
    id                          BIGSERIAL PRIMARY KEY,
    tienda_id                   BIGINT NOT NULL REFERENCES tiendas(id),
    person_user_id              BIGINT NOT NULL REFERENCES users(id),
    amount                      NUMERIC(12,2) NOT NULL,
    reason                      VARCHAR(150),
    source_cut_id               BIGINT REFERENCES cash_cuts(id),
    status                      VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    created_at                  TIMESTAMP(6),
    paid_at                     TIMESTAMP(6),
    paid_registered_by_user_id  BIGINT REFERENCES users(id),
    payment_source              VARCHAR(10),
    payment_cut_id              BIGINT REFERENCES cash_cuts(id),
    external_method             VARCHAR(15),
    external_paid_by_user_id    BIGINT REFERENCES users(id),
    reference                   VARCHAR(100),
    receipt_path                VARCHAR(300)
);
CREATE INDEX IF NOT EXISTS idx_reimbursements_tienda_status ON reimbursements(tienda_id, status);

-- ── Movimientos de efectivo ─────────────────────────────────────────
CREATE TABLE IF NOT EXISTS cash_movements (
    id                      BIGSERIAL PRIMARY KEY,
    tienda_id               BIGINT NOT NULL REFERENCES tiendas(id),
    cash_register_id        BIGINT REFERENCES cash_registers(id),
    cash_cut_id             BIGINT REFERENCES cash_cuts(id),
    received_by_cut_id      BIGINT REFERENCES cash_cuts(id),
    type                    VARCHAR(20) NOT NULL,
    amount                  NUMERIC(12,2) NOT NULL,
    concept                 VARCHAR(150),
    person_user_id          BIGINT REFERENCES users(id),
    destination             VARCHAR(10),
    reference               VARCHAR(100),
    receipt_path            VARCHAR(300),
    registered_by_user_id   BIGINT NOT NULL REFERENCES users(id),
    created_at              TIMESTAMP(6),
    occurred_at             TIMESTAMP(6),
    late_registered         BOOLEAN NOT NULL DEFAULT FALSE,
    expected_remaining      NUMERIC(12,2),
    counted_remaining       NUMERIC(12,2),
    reimbursement_id        BIGINT REFERENCES reimbursements(id),
    -- préstamos entre cajas: la otra caja (LOAN_OUT en la que presta, LOAN_IN en la que recibe)
    counterpart_register_id BIGINT REFERENCES cash_registers(id)
);
ALTER TABLE cash_movements ADD COLUMN IF NOT EXISTS counterpart_register_id BIGINT REFERENCES cash_registers(id);
CREATE INDEX IF NOT EXISTS idx_cash_movements_cut      ON cash_movements(cash_cut_id);
CREATE INDEX IF NOT EXISTS idx_cash_movements_register ON cash_movements(cash_register_id);
CREATE INDEX IF NOT EXISTS idx_cash_movements_received ON cash_movements(received_by_cut_id);

-- El backend también rehace esta restricción al arrancar (CashMovementTypeConstraintMigration).
ALTER TABLE cash_movements DROP CONSTRAINT IF EXISTS cash_movements_type_check;
ALTER TABLE cash_movements ADD CONSTRAINT cash_movements_type_check
    CHECK (type IN ('EXPENSE', 'WITHDRAWAL', 'REIMBURSEMENT', 'CONTRIBUTION', 'FLOAT', 'SHORTAGE_PAYMENT', 'LOAN_OUT', 'LOAN_IN'));

-- ── Faltantes y sus abonos ──────────────────────────────────────────
CREATE TABLE IF NOT EXISTS shortages (
    id                  BIGSERIAL PRIMARY KEY,
    tienda_id           BIGINT NOT NULL REFERENCES tiendas(id),
    person_user_id      BIGINT NOT NULL REFERENCES users(id),
    amount              NUMERIC(12,2) NOT NULL,
    paid_amount         NUMERIC(12,2) NOT NULL DEFAULT 0,
    status              VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    source_cut_id       BIGINT REFERENCES cash_cuts(id),
    reason              VARCHAR(200),
    -- de una entrega entre turnos (HANDOVER) o del arqueo al cerrar (CLOSE)
    origin              VARCHAR(10) NOT NULL DEFAULT 'HANDOVER',
    created_by_user_id  BIGINT NOT NULL REFERENCES users(id),
    created_at          TIMESTAMP(6)
);
ALTER TABLE shortages ADD COLUMN IF NOT EXISTS origin VARCHAR(10) NOT NULL DEFAULT 'HANDOVER';
CREATE INDEX IF NOT EXISTS idx_shortages_tienda_status ON shortages(tienda_id, status);

CREATE TABLE IF NOT EXISTS shortage_payments (
    id                      BIGSERIAL PRIMARY KEY,
    shortage_id             BIGINT NOT NULL REFERENCES shortages(id),
    amount                  NUMERIC(12,2) NOT NULL,
    method                  VARCHAR(15) NOT NULL,
    cash_movement_id        BIGINT REFERENCES cash_movements(id),
    reference               VARCHAR(100),
    receipt_path            VARCHAR(300),
    registered_by_user_id   BIGINT NOT NULL REFERENCES users(id),
    created_at              TIMESTAMP(6)
);
CREATE INDEX IF NOT EXISTS idx_shortage_payments_shortage ON shortage_payments(shortage_id);

-- ── Destinatarios del reporte de fin de día ─────────────────────────
CREATE TABLE IF NOT EXISTS cash_report_recipients (
    id         BIGSERIAL PRIMARY KEY,
    tienda_id  BIGINT NOT NULL REFERENCES tiendas(id),
    user_id    BIGINT REFERENCES users(id),
    email      VARCHAR(150)
);
CREATE INDEX IF NOT EXISTS idx_cash_report_recipients_tienda ON cash_report_recipients(tienda_id);

-- ── Cortes ya cerrados: estado del arqueo (el backend también lo hace al arrancar) ──
UPDATE cash_cuts SET arqueo_status = 'DIFFERENCE' WHERE status = 'CLOSED' AND arqueo_status = 'NONE' AND counted_amount IS NOT NULL AND cash_difference <> 0;
UPDATE cash_cuts SET arqueo_status = 'OK'         WHERE status = 'CLOSED' AND arqueo_status = 'NONE' AND counted_amount IS NOT NULL AND cash_difference = 0;

-- ── Movimientos manuales de RestoPOS (movimientos_caja) → cash_movements ──
-- Solo si la tabla vieja existe; no duplica si ya se migró (mismo corte, hora y monto).
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'movimientos_caja') THEN
        INSERT INTO cash_movements (tienda_id, cash_cut_id, type, amount, concept, registered_by_user_id,
                                    created_at, occurred_at, late_registered)
        SELECT c.tienda_id, mc.cash_cut_id,
               CASE WHEN mc.tipo = 'SALIDA' OR (mc.tipo = 'AJUSTE' AND mc.monto < 0) THEN 'EXPENSE' ELSE 'FLOAT' END,
               ABS(mc.monto),
               LEFT(mc.motivo, 150),
               mc.usuario_id,
               mc.created_at, mc.created_at, FALSE
        FROM movimientos_caja mc
        JOIN cash_cuts c ON c.id = mc.cash_cut_id
        WHERE c.tienda_id IS NOT NULL
          AND NOT EXISTS (SELECT 1 FROM cash_movements cm
                          WHERE cm.cash_cut_id = mc.cash_cut_id AND cm.created_at = mc.created_at
                            AND cm.amount = ABS(mc.monto));
    END IF;
END $$;

COMMIT;
