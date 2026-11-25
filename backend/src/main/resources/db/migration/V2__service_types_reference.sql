-- ============================================================
--  V2. Справочник видов услуг.
--  В КР1 вид услуги был enum ServiceType в Java и строкой в bookings.
--  Теперь это отдельная таблица: администратор может добавлять,
--  изменять, включать и отключать услуги без удаления.
-- ============================================================

CREATE TABLE service_types (
    id               UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    code             VARCHAR(30)    NOT NULL,
    name             VARCHAR(100)   NOT NULL,
    description      VARCHAR(500),
    base_price       NUMERIC(10, 2) NOT NULL,
    duration_minutes INTEGER        NOT NULL,
    active           BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version          BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uq_service_types_code UNIQUE (code),
    CONSTRAINT chk_service_types_code CHECK (code ~ '^[A-Z][A-Z0-9_]{1,29}$'),
    CONSTRAINT chk_service_types_price CHECK (base_price >= 0 AND base_price <= 1000000),
    CONSTRAINT chk_service_types_duration CHECK (duration_minutes BETWEEN 5 AND 1440)
);

-- название услуги уникально без учёта регистра
CREATE UNIQUE INDEX uq_service_types_name ON service_types (lower(name));
CREATE INDEX idx_service_types_active ON service_types (active);

-- первые четыре услуги — это значения enum ServiceType из КР1
INSERT INTO service_types (code, name, description, base_price, duration_minutes, active) VALUES
    ('BODY_WASH',    'Мойка кузова',      'Бесконтактная мойка кузова и дисков',            500,  30,  TRUE),
    ('COMPLEX',      'Комплексная мойка', 'Кузов, пылесос салона, протирка стёкол',         1200, 60,  TRUE),
    ('DRY_CLEANING', 'Химчистка салона',  'Химчистка сидений, потолка и ковров',            3000, 180, TRUE),
    ('POLISHING',    'Полировка кузова',  'Абразивная полировка и защитное покрытие',       5000, 240, TRUE),
    ('ENGINE_WASH',  'Мойка двигателя',   'Аккуратная мойка моторного отсека',              1500, 45,  TRUE),
    ('WAX',          'Нанесение воска',   'Горячий воск после мойки (временно не оказывается)', 800, 20, FALSE);
