-- ============================================================
--  V3. Доработка таблиц КР1 под REST API:
--   * идентификаторы clients и bookings переводятся на UUID;
--   * у записи появляется внешний код (CW-000001), по нему работает импорт;
--   * вид услуги становится внешним ключом на справочник service_types;
--   * новые поля: оплата, риск неоплаты, архив, даты, version;
--   * теги записи и индексы для поиска.
-- ============================================================

-- ---------- 1. Клиенты: SERIAL -> UUID ----------
ALTER TABLE clients ADD COLUMN uuid UUID NOT NULL DEFAULT gen_random_uuid();

ALTER TABLE bookings ADD COLUMN client_uuid UUID;
UPDATE bookings b SET client_uuid = c.uuid FROM clients c WHERE c.id = b.client_id;
ALTER TABLE bookings DROP CONSTRAINT bookings_client_id_fkey;
ALTER TABLE bookings DROP COLUMN client_id;
ALTER TABLE bookings RENAME COLUMN client_uuid TO client_id;
ALTER TABLE bookings ALTER COLUMN client_id SET NOT NULL;

ALTER TABLE clients DROP CONSTRAINT clients_pkey;
ALTER TABLE clients DROP COLUMN id;
ALTER TABLE clients RENAME COLUMN uuid TO id;
ALTER TABLE clients ADD CONSTRAINT clients_pkey PRIMARY KEY (id);
ALTER TABLE bookings ADD CONSTRAINT fk_bookings_client FOREIGN KEY (client_id) REFERENCES clients (id);

ALTER TABLE clients ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'Europe/Moscow';
ALTER TABLE clients ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE clients ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
UPDATE clients SET updated_at = created_at;
-- формат телефона тот же, что проверялся в ClientService из КР1
ALTER TABLE clients ADD CONSTRAINT chk_clients_phone CHECK (phone ~ '^\+7-[0-9]{3}-[0-9]{3}-[0-9]{2}-[0-9]{2}$');

-- ---------- 2. Записи: SERIAL -> UUID + внешний код ----------
ALTER TABLE bookings ADD COLUMN uuid UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE bookings ADD COLUMN code VARCHAR(20);
UPDATE bookings SET code = 'CW-' || lpad(id::text, 6, '0');
ALTER TABLE bookings ALTER COLUMN code SET NOT NULL;
ALTER TABLE bookings ADD CONSTRAINT uq_bookings_code UNIQUE (code);

-- последовательность для новых кодов продолжает нумерацию КР1
CREATE SEQUENCE booking_code_seq;
SELECT setval('booking_code_seq', (SELECT COALESCE(MAX(id), 0) + 1 FROM bookings), false);

ALTER TABLE bookings DROP CONSTRAINT bookings_pkey;
ALTER TABLE bookings DROP COLUMN id;
ALTER TABLE bookings RENAME COLUMN uuid TO id;
ALTER TABLE bookings ADD CONSTRAINT bookings_pkey PRIMARY KEY (id);

-- ---------- 3. Вид услуги: строка -> внешний ключ на справочник ----------
ALTER TABLE bookings ADD COLUMN service_type_id UUID;
UPDATE bookings b SET service_type_id = s.id FROM service_types s WHERE s.code = b.service_type;
ALTER TABLE bookings ALTER COLUMN service_type_id SET NOT NULL;
ALTER TABLE bookings ADD CONSTRAINT fk_bookings_service_type
    FOREIGN KEY (service_type_id) REFERENCES service_types (id);
ALTER TABLE bookings DROP CONSTRAINT chk_service_type;
ALTER TABLE bookings DROP COLUMN service_type;

-- ---------- 4. Новые поля записи ----------
ALTER TABLE bookings ADD COLUMN customer_comment VARCHAR(500);
ALTER TABLE bookings ADD COLUMN paid BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE bookings ADD COLUMN payment_risk VARCHAR(10) NOT NULL DEFAULT 'LOW';
ALTER TABLE bookings ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE bookings ADD COLUMN archived_at TIMESTAMPTZ;
ALTER TABLE bookings ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'Europe/Moscow';
ALTER TABLE bookings ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE bookings ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
UPDATE bookings SET updated_at = created_at;

ALTER TABLE bookings ADD CONSTRAINT chk_bookings_payment_risk CHECK (payment_risk IN ('LOW', 'MEDIUM', 'HIGH'));
ALTER TABLE bookings ADD CONSTRAINT chk_bookings_price_max CHECK (price <= 1000000);
ALTER TABLE bookings ADD CONSTRAINT chk_bookings_archived_at
    CHECK ((archived AND archived_at IS NOT NULL) OR (NOT archived AND archived_at IS NULL));

-- данные КР1: завершённые записи считаем оплаченными, кроме CW-000012 (пример долга);
-- химчистку CW-000003 клиент оплатил заранее
UPDATE bookings SET paid = TRUE WHERE status = 'COMPLETED' AND code <> 'CW-000012';
UPDATE bookings SET paid = TRUE WHERE code = 'CW-000003';

-- риск неоплаты по тем же правилам, что и PaymentRisk.calculate() в Java
UPDATE bookings SET payment_risk = CASE
    WHEN status = 'CANCELLED' OR paid OR price = 0 THEN 'LOW'
    WHEN status = 'COMPLETED' THEN 'HIGH'
    WHEN price >= 3000 THEN 'HIGH'
    WHEN price >= 1000 OR status = 'IN_PROGRESS' THEN 'MEDIUM'
    ELSE 'LOW'
END;

-- ---------- 5. Теги записи ----------
CREATE TABLE booking_tags (
    booking_id UUID        NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    tag        VARCHAR(30) NOT NULL,
    CONSTRAINT pk_booking_tags PRIMARY KEY (booking_id, tag),
    CONSTRAINT chk_booking_tags_format CHECK (tag = lower(tag) AND char_length(tag) BETWEEN 1 AND 30 AND tag !~ '\s')
);

INSERT INTO booking_tags (booking_id, tag)
SELECT id, 'постоянный' FROM bookings WHERE code IN ('CW-000001', 'CW-000002', 'CW-000011');
INSERT INTO booking_tags (booking_id, tag)
SELECT id, 'vip' FROM bookings WHERE code IN ('CW-000009', 'CW-000010');
INSERT INTO booking_tags (booking_id, tag)
SELECT id, 'срочно' FROM bookings WHERE code = 'CW-000005';

-- ---------- 6. Индексы для поиска и фильтров ----------
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_bookings_client          ON bookings (client_id);
CREATE INDEX idx_bookings_service_type    ON bookings (service_type_id);
CREATE INDEX idx_bookings_status          ON bookings (status);
CREATE INDEX idx_bookings_payment_risk    ON bookings (payment_risk);
CREATE INDEX idx_bookings_paid            ON bookings (paid);
CREATE INDEX idx_bookings_price           ON bookings (price);
CREATE INDEX idx_bookings_archived_date   ON bookings (archived, scheduled_at DESC);
CREATE INDEX idx_booking_tags_tag         ON booking_tags (tag);
-- триграммные индексы ускоряют поиск LIKE '%текст%'
CREATE INDEX idx_bookings_code_trgm       ON bookings USING gin (lower(code) gin_trgm_ops);
CREATE INDEX idx_bookings_car_number_trgm ON bookings USING gin (lower(car_number) gin_trgm_ops);
CREATE INDEX idx_bookings_car_model_trgm  ON bookings USING gin (lower(car_model) gin_trgm_ops);
CREATE INDEX idx_clients_full_name_trgm   ON clients USING gin (lower(full_name) gin_trgm_ops);
CREATE INDEX idx_clients_phone_trgm       ON clients USING gin (phone gin_trgm_ops);
