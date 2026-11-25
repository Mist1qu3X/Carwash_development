-- ============================================================
--  V4. Пользователи, роли и refresh-сессии.
--  Пароли хранятся только в виде BCrypt-хеша,
--  refresh-токены — только в виде SHA-256 хеша.
-- ============================================================

CREATE TABLE roles (
    code  VARCHAR(20)  PRIMARY KEY,
    title VARCHAR(100) NOT NULL
);

INSERT INTO roles (code, title) VALUES
    ('VIEWER',  'Оператор: просмотр'),
    ('MANAGER', 'Менеджер: редактирование'),
    ('ADMIN',   'Администратор');

CREATE TABLE users (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email                 VARCHAR(254) NOT NULL,
    full_name             VARCHAR(100) NOT NULL,
    password_hash         VARCHAR(100) NOT NULL,
    active                BOOLEAN      NOT NULL DEFAULT TRUE,
    must_change_password  BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_login_attempts INTEGER      NOT NULL DEFAULT 0,
    locked_until          TIMESTAMPTZ,
    last_login_at         TIMESTAMPTZ,
    password_changed_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version               BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT chk_users_email_lower CHECK (email = lower(email)),
    CONSTRAINT chk_users_failed_attempts CHECK (failed_login_attempts >= 0)
);

CREATE UNIQUE INDEX uq_users_email ON users (email);
CREATE INDEX idx_users_full_name ON users (lower(full_name));

CREATE TABLE user_roles (
    user_id   UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_code VARCHAR(20) NOT NULL REFERENCES roles (code),
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_code)
);

CREATE TABLE refresh_sessions (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ  NOT NULL,
    revoked_at TIMESTAMPTZ,
    ip_address VARCHAR(45),
    user_agent VARCHAR(255),
    CONSTRAINT uq_refresh_sessions_token UNIQUE (token_hash),
    CONSTRAINT chk_refresh_sessions_dates CHECK (expires_at > created_at)
);

CREATE INDEX idx_refresh_sessions_user ON refresh_sessions (user_id);
CREATE INDEX idx_refresh_sessions_expires ON refresh_sessions (expires_at);
