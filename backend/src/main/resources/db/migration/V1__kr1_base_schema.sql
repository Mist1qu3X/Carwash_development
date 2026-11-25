-- ============================================================
--  V1. Базовая схема из КР1 (консольное приложение "Автомойка").
--  Перенесена без изменений, чтобы сохранить связь с КР1.
--  Все доработки для backend выполняются следующими миграциями.
-- ============================================================

CREATE TABLE clients (
    id         SERIAL PRIMARY KEY,
    full_name  VARCHAR(100) NOT NULL,
    phone      VARCHAR(20)  NOT NULL UNIQUE,
    email      VARCHAR(100),
    created_at TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE bookings (
    id           SERIAL PRIMARY KEY,
    client_id    INTEGER        NOT NULL REFERENCES clients (id),
    car_number   VARCHAR(20)    NOT NULL,
    car_model    VARCHAR(50),
    service_type VARCHAR(20)    NOT NULL,
    status       VARCHAR(20)    NOT NULL DEFAULT 'CREATED',
    scheduled_at TIMESTAMP      NOT NULL,
    price        NUMERIC(10, 2) NOT NULL DEFAULT 0 CHECK (price >= 0),
    created_at   TIMESTAMP      NOT NULL DEFAULT now(),
    CONSTRAINT chk_service_type CHECK (service_type IN ('BODY_WASH', 'COMPLEX', 'DRY_CLEANING', 'POLISHING')),
    CONSTRAINT chk_status CHECK (status IN ('CREATED', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'))
);

-- тестовые данные КР1
INSERT INTO clients (full_name, phone, email) VALUES ('Иван Петров', '+7-900-111-11-11', 'ivan@mail.ru');
INSERT INTO clients (full_name, phone, email) VALUES ('Мария Сидорова', '+7-900-222-22-22', 'maria@mail.ru');
INSERT INTO clients (full_name, phone, email) VALUES ('Алексей Смирнов', '+7-900-333-33-33', NULL);
INSERT INTO clients (full_name, phone, email) VALUES ('Ольга Кузнецова', '+7-900-444-44-44', 'olga@mail.ru');
INSERT INTO clients (full_name, phone, email) VALUES ('Дмитрий Волков', '+7-900-555-55-55', 'dmitry@mail.ru');

INSERT INTO bookings (client_id, car_number, car_model, service_type, status, scheduled_at, price) VALUES (1, 'А123ВС77', 'Toyota Camry', 'COMPLEX', 'COMPLETED', '2026-09-01 10:00', 1200);
INSERT INTO bookings (client_id, car_number, car_model, service_type, status, scheduled_at, price) VALUES (1, 'А123ВС77', 'Toyota Camry', 'BODY_WASH', 'COMPLETED', '2026-09-10 12:30', 500);
INSERT INTO bookings (client_id, car_number, car_model, service_type, status, scheduled_at, price) VALUES (2, 'В456ОР99', 'Kia Rio', 'DRY_CLEANING', 'CONFIRMED', '2026-10-05 14:00', 3000);
INSERT INTO bookings (client_id, car_number, car_model, service_type, status, scheduled_at, price) VALUES (2, 'В456ОР99', 'Kia Rio', 'BODY_WASH', 'CREATED', '2026-10-15 09:00', 500);
INSERT INTO bookings (client_id, car_number, car_model, service_type, status, scheduled_at, price) VALUES (3, 'Е789КХ50', 'Lada Vesta', 'POLISHING', 'IN_PROGRESS', '2026-09-20 16:00', 5000);
INSERT INTO bookings (client_id, car_number, car_model, service_type, status, scheduled_at, price) VALUES (3, 'Е789КХ50', 'Lada Vesta', 'COMPLEX', 'CANCELLED', '2026-09-18 11:00', 1200);
INSERT INTO bookings (client_id, car_number, car_model, service_type, status, scheduled_at, price) VALUES (4, 'О321УК77', 'Hyundai Solaris', 'BODY_WASH', 'COMPLETED', '2026-08-25 08:30', 500);
INSERT INTO bookings (client_id, car_number, car_model, service_type, status, scheduled_at, price) VALUES (4, 'О321УК77', 'Hyundai Solaris', 'COMPLEX', 'CONFIRMED', '2026-10-20 13:00', 1200);
INSERT INTO bookings (client_id, car_number, car_model, service_type, status, scheduled_at, price) VALUES (5, 'Т555ММ97', 'BMW X5', 'POLISHING', 'CREATED', '2026-10-25 15:30', 5500);
INSERT INTO bookings (client_id, car_number, car_model, service_type, status, scheduled_at, price) VALUES (5, 'Т555ММ97', 'BMW X5', 'DRY_CLEANING', 'COMPLETED', '2026-09-05 17:00', 3200);
INSERT INTO bookings (client_id, car_number, car_model, service_type, status, scheduled_at, price) VALUES (1, 'А123ВС77', 'Toyota Camry', 'POLISHING', 'CONFIRMED', '2026-11-01 10:00', 5000);
INSERT INTO bookings (client_id, car_number, car_model, service_type, status, scheduled_at, price) VALUES (2, 'В456ОР99', 'Kia Rio', 'COMPLEX', 'COMPLETED', '2026-09-12 12:00', 1300);
