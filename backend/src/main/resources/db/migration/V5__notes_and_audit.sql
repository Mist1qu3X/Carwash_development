-- ============================================================
--  V5. Заметки к записям и журнал аудита.
-- ============================================================

CREATE TABLE booking_notes (
    id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id  UUID          NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    author_id   UUID          REFERENCES users (id) ON DELETE SET NULL,
    author_name VARCHAR(100)  NOT NULL,
    text        VARCHAR(2000) NOT NULL,
    pinned      BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version     BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT chk_booking_notes_text CHECK (char_length(btrim(text)) > 0)
);

CREATE INDEX idx_booking_notes_booking ON booking_notes (booking_id, pinned DESC, created_at DESC);

-- пример заметки из данных КР1
INSERT INTO booking_notes (booking_id, author_name, text, pinned)
SELECT id, 'Система', 'Клиент просит позвонить за час до начала полировки.', TRUE
FROM bookings WHERE code = 'CW-000009';

CREATE TABLE audit_events (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    user_id        UUID          REFERENCES users (id) ON DELETE SET NULL,
    user_email     VARCHAR(254),
    event_type     VARCHAR(40)   NOT NULL,
    entity_type    VARCHAR(40),
    entity_id      UUID,
    description    VARCHAR(1000) NOT NULL,
    correlation_id VARCHAR(64),
    ip_address     VARCHAR(45)
);

CREATE INDEX idx_audit_events_created ON audit_events (created_at DESC);
CREATE INDEX idx_audit_events_type ON audit_events (event_type);
CREATE INDEX idx_audit_events_entity ON audit_events (entity_type, entity_id);
CREATE INDEX idx_audit_events_user ON audit_events (user_email);
