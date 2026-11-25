# Автомойка — информационная система записи на мойку

Проект ведётся с КР1 и развивается от работы к работе:

| Работа | Что сделано | Где лежит |
|---|---|---|
| КР1 | Консольное приложение, PostgreSQL через JDBC | `src/`, `pom.xml` |
| КР2 | Настольный клиент JavaFX | `src/main/java/carwash/fx/` (тот же Maven-проект, что КР1) |
| **КР3** | **Spring Boot REST API + React web-интерфейс, PostgreSQL, Flyway, JWT, Docker** | **`backend/`, `frontend/`, `.env`** |
| КР4 | Android-клиент, который использует REST API из КР3 | — |

---

## КР3. Backend и frontend

**Основная сущность** — запись на мойку (`Booking`), **справочник** — виды услуг (`ServiceType`).
База из КР1 (таблицы `clients` и `bookings`) — основа серверной базы: миграция `V1` повторяет схему КР1 без изменений,
дальше Flyway доводит её до нужного вида.

### Быстрый запуск (Docker)

Нужен только запущенный Docker Desktop (JDK, Maven и Node.js для запуска не нужны — всё собирается внутри Docker).

```bash
# macOS / Linux, из папки Carwash_development
./backend/start-compose.sh

# Windows (PowerShell), из папки Carwash_development
powershell -ExecutionPolicy Bypass -File .\backend\start-compose.ps1

# или напрямую
docker compose --env-file .env -f backend/docker-compose.yml up -d --build --wait
```

Первая сборка образа занимает несколько минут (скачиваются зависимости Maven и npm).

| Назначение | Адрес |
|---|---|
| Web-интерфейс (React) | http://localhost:8080/ |
| Swagger UI (только администратор, HTTP Basic) | http://localhost:8080/swagger-ui.html |
| REST API | http://localhost:8080/api |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
| Health | http://localhost:8080/actuator/health |
| PostgreSQL | `jdbc:postgresql://localhost:55432/carwash` (пользователь `carwash_app`) |

**Первый вход:** `admin@example.org`, временный пароль — значение `BOOTSTRAP_ADMIN_PASSWORD` из `.env`.
Система сразу потребует сменить его на свой (от 12 символов, заглавная и строчная буква, цифра, спецсимвол).
Пароль PostgreSQL — `POSTGRES_PASSWORD` из `.env`.

Остановка: `docker compose --env-file .env -f backend/docker-compose.yml down` — данные БД сохраняются в томе
`carwash_carwash-pgdata`. Полностью пересоздать базу (все данные пропадут): добавить к команде `-v`.

### Файлы настроек

* `.env` — реальные значения (секреты сгенерированы случайно; в git не коммитится).
* `.env.example` — шаблон без секретов, для сдачи и для новых участников команды.
* `PERMANENT_DELETE_ENABLED=true` в `.env` включает окончательное удаление записей (для демонстрации); в шаблоне — `false`.

### Разработка без Docker

```bash
# 1) база в Docker
docker compose --env-file .env -f backend/docker-compose.yml up -d postgres

# 2) backend (JDK 21): в IntelliJ IDEA запустить CarwashBackendApplication
#    с переменными из .env, указав SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:55432/carwash
cd backend && mvn spring-boot:run

# 3) frontend с горячей перезагрузкой: http://localhost:5173 (запросы /api проксируются на :8080)
cd frontend && npm install && npm run dev

# сборка React внутрь backend (тогда UI доступен и на :8080)
cd frontend && npm run build
```

Тесты backend (H2 в режиме PostgreSQL, Docker не нужен): `cd backend && mvn test`.

### Как Student Hub перенесён на тему «Автомойка»

| Student Hub | Автомойка | Что это |
|---|---|---|
| `Student` | `Booking` | запись на мойку — основная сущность |
| `StudyGroup` | `ServiceType` | вид услуги — справочник (включение/отключение без удаления) |
| `StudentStatus` | `BookingStatus` | Создана → Подтверждена → В работе → Завершена / Отменена (правила из КР1) |
| `RiskLevel` | `PaymentRisk` | риск неоплаты — вычисляемый показатель |
| `StudentNote` | `BookingNote` | заметки сотрудников к записи |
| `student_tag` | `booking_tags` | теги записи |
| `UserAccount`, `RefreshSession`, `AuditEvent` | те же | учётные записи, refresh-сессии, аудит |
| `AnalyticsResponse` | `AnalyticsResponse` | сводка аналитики |
| — | `Client` | клиент из КР1 (запись ссылается на клиента) |

**Поля записи:** UUID, внешний код `CW-000001`, клиент, гос. номер, модель, пожелания клиента, вид услуги,
статус, дата и время, цена (числовой показатель), признак «оплачено» (логический), теги, риск неоплаты,
даты создания и изменения, `version`, признак архива.

**Риск неоплаты** (`PaymentRisk.calculate`, пересчитывается при каждом изменении статуса, оплаты или цены):

1. отменена, уже оплачена или цена 0 → низкий;
2. услуга оказана (`COMPLETED`), но не оплачена → высокий;
3. неоплаченная сумма от 3000 ₽ → высокий;
4. от 1000 ₽ или машина уже «в работе» → средний;
5. иначе → низкий.

### Роли

| Роль | Название | Права |
|---|---|---|
| `VIEWER` | Оператор: просмотр | просмотр записей, клиентов, справочника, аналитики, экспорт CSV |
| `MANAGER` | Менеджер: редактирование | + создание и изменение записей и клиентов, архив, заметки, массовые операции, импорт CSV |
| `ADMIN` | Администратор | + справочник услуг, пользователи, аудит, окончательное удаление, Swagger |

Права наследуются (`RoleHierarchy`): ADMIN ⊃ MANAGER ⊃ VIEWER.

### Структура backend

```
backend/src/main/java/carwash/backend/
├── controller/   REST-контроллеры (+ SpaController для маршрутов React)
├── service/      бизнес-логика и транзакции
├── repository/   Spring Data JPA
├── domain/       JPA-сущности и enum
├── dto/          входные и выходные модели API (records + Bean Validation)
├── security/     JWT, фильтр, текущий пользователь, политика паролей
├── filter/       поиск через Specification, белые списки сортировки, пагинация
├── error/        исключения и единый JSON-формат ошибок
└── config/       Spring Security, OpenAPI, параметры, correlation ID, bootstrap-администратор
backend/src/main/resources/db/migration/   миграции Flyway V1–V5
frontend/src/                              React: pages/, components/, api.js, auth.jsx
```

### Миграции Flyway

| Версия | Что делает |
|---|---|
| `V1__kr1_base_schema` | схема и тестовые данные КР1 без изменений |
| `V2__service_types_reference` | справочник видов услуг (бывший enum `ServiceType`) |
| `V3__bookings_backend_fields` | переход `clients`/`bookings` на UUID, код записи, FK на справочник, оплата, риск, архив, `version`, теги, индексы (в т.ч. триграммные для поиска) |
| `V4__security_tables` | `users`, `roles`, `user_roles`, `refresh_sessions` |
| `V5__notes_and_audit` | `booking_notes`, `audit_events` |

Hibernate работает в режиме `ddl-auto: validate` — схему меняет только Flyway.

### REST API

| Назначение | Метод и адрес | Роль |
|---|---|---|
| Вход | `POST /api/auth/login` | — |
| Обновление токена | `POST /api/auth/refresh` (тело `{refreshToken}` или cookie) | — |
| Выход | `POST /api/auth/logout` | — |
| Текущий пользователь | `GET /api/auth/me` | любой |
| Смена пароля | `POST /api/auth/change-password` | любой |
| Список записей | `GET /api/bookings` | VIEWER |
| Карточка | `GET /api/bookings/{id}` | VIEWER |
| Создание | `POST /api/bookings` | MANAGER |
| Изменение (с `version`) | `PUT /api/bookings/{id}` | MANAGER |
| Архив | `DELETE /api/bookings/{id}` | MANAGER |
| Восстановление | `POST /api/bookings/{id}/restore` | MANAGER |
| Окончательное удаление | `DELETE /api/bookings/{id}/permanent` | ADMIN |
| Массовые операции | `POST /api/bookings/bulk` | MANAGER |
| Проверка CSV | `POST /api/bookings/import/preview` | MANAGER |
| Применение CSV | `POST /api/bookings/import/apply` | MANAGER |
| Экспорт CSV | `GET /api/bookings/export` | VIEWER |
| Заметки | `GET/POST /api/bookings/{id}/notes`, `PUT/DELETE …/notes/{noteId}` | VIEWER / MANAGER |
| Справочник услуг | `GET /api/service-types`, `POST`, `PUT /{id}`, `POST /{id}/enable`, `/{id}/disable` | VIEWER / ADMIN |
| Клиенты | `GET/POST /api/clients`, `GET/PUT /api/clients/{id}` | VIEWER / MANAGER |
| Аналитика | `GET /api/analytics/summary` | VIEWER |
| Пользователи | `GET/POST /api/admin/users`, `GET/PUT /{id}`, `POST /{id}/reset-password` | ADMIN |
| Аудит | `GET /api/admin/audit` | ADMIN |
| Значения enum | `GET /api/meta` | любой |

**Параметры списка записей:** `q`, `serviceTypeId`, `clientId`, `status`, `paid`, `risk`, `tag`,
`archive=ACTIVE|ARCHIVED|ALL`, `price` + `priceOp=EQ|GT|GTE|LT|LTE`, `scheduledFrom`, `scheduledTo`,
`page`, `size` (не больше 100), `sort=поле,asc|desc` (`scheduledAt`, `price`, `code`, `status`, `carNumber`,
`clientName`, `serviceType`, `createdAt`, `updatedAt`).

**Ошибки** всегда в одном формате:

```json
{ "timestamp": "…", "status": 409, "error": "VERSION_CONFLICT",
  "message": "Данные уже изменил другой пользователь…", "path": "/api/bookings/…",
  "correlationId": "…", "details": [] }
```

### Безопасность — коротко

* Пароли — BCrypt (strength 12), в ответах API никогда не возвращаются.
* Access JWT живёт 15 минут; refresh-токен — случайные 256 бит, в БД хранится только SHA-256.
  При обновлении старый refresh-токен отзывается (ротация), повторное использование отозванного токена
  завершает все сессии пользователя.
* Web-интерфейс хранит access-токен только в памяти, refresh — в HttpOnly-cookie `SameSite=Strict`;
  Android-клиент (КР4) получает refresh-токен в теле ответа.
* После 5 неверных паролей вход блокируется на 15 минут (HTTP 429).
* Отключённый пользователь не может войти, его сессии сразу отзываются; смена или сброс пароля
  делает недействительными ранее выданные access-токены.
* SQL не собирается из строк: поиск построен на JPA Criteria (`BookingSpecifications`),
  `%` и `_` в поиске экранируются, поля сортировки — только из белого списка.
* CSV-экспорт защищён от формул (значения, начинающиеся с `= + - @`, экранируются).

### Тесты

| Класс | Что проверяет |
|---|---|
| `CarwashBackendApplicationTests` | smoke: Spring Context, health, correlation ID, Swagger закрыт |
| `PaymentRiskTest` | бизнес-правило риска неоплаты и переходы статусов |
| `BookingSearchTest` | поиск, фильтры, экранирование `%`, сортировка, пагинация, лимит страницы |
| `PasswordPolicyTest` | политика паролей, временные пароли, BCrypt, хеш refresh-токена |
| `ApiIntegrationTest` | сценарии API: роли, смена пароля, 401/403/404/409, CRUD, архив, заметки, массовые операции, CSV, блокировка входа, refresh/logout, аудит |

### Что показать на защите

1. `./backend/start-compose.sh` → `docker compose … ps` (оба контейнера `healthy`) → http://localhost:8080/actuator/health.
2. Вход `admin@example.org` с временным паролем → обязательная смена пароля.
3. **Пользователи** → создать менеджера и оператора (временный пароль показывается один раз) → войти под оператором
   в другом браузере: кнопок изменения нет, запрос на запись — 403.
4. **Записи** → «+ Новая запись» (цена подставляется из справочника) → изменить → поиск «kia», фильтры по статусу,
   риску, тегу, цене «> 3000», сортировка кликом по заголовку, переключение страниц.
5. Открыть одну запись в двух вкладках и сохранить обе — вторая получит 409 (конфликт версий).
6. «В архив» → фильтр «Архив» → «Восстановить»; «Удалить» (только администратор, только из архива).
7. Отметить несколько записей → «Сменить статус»: недопустимые переходы попадут в список ошибок.
8. Карточка записи → заметки (закрепить, изменить, удалить).
9. «Экспорт CSV» (учитывает фильтр) → «Импорт CSV» → «Скачать пример файла» → «1. Проверить» → «2. Применить».
10. **Аналитика**, **Аудит** (поиск по событию, correlation ID), **Услуги** (отключить услугу — её нельзя выбрать в новой записи).
11. Swagger: http://localhost:8080/swagger-ui.html (логин и пароль администратора) → `POST /api/auth/login` → Authorize.
12. Структура БД: `docker exec -it carwash-postgres psql -U carwash_app -d carwash -c '\dt'`,
    история миграций: `select version, description from flyway_schema_history;`.

---

## КР1. Консольное приложение

Консольное приложение на Java: ведёт клиентов и записи на автомойку.
Данные хранятся в PostgreSQL, доступ через JDBC. Архитектура многослойная:

```
Меню (UI)  ->  Сервис (логика)  ->  Репозиторий (JDBC)  ->  PostgreSQL
```

### Возможности
- клиенты: добавить / список / поиск по ID / изменить / удалить;
- записи: создать / список / изменить / сменить статус / удалить;
- поиск (по гос. номеру, по имени клиента);
- фильтрация (по статусу, по услуге, по диапазону дат);
- сортировка (по дате, по цене, по статусу);
- статистика;
- экспорт в Excel (.xlsx) и CSV.

### Что нужно
- JDK 17 или новее;
- PostgreSQL;
- Maven (или IntelliJ IDEA — в ней Maven уже встроен).

### Настройка базы данных
1. Установить и запустить PostgreSQL. Утилиты `createuser` и `createdb` должны быть доступны в терминале.
2. Создать отдельного пользователя приложения и базу:
   ```sql
   createuser -P carwash
   createdb -O carwash carwash
   ```
   `createuser` запросит пароль интерактивно.
3. Создать локальный конфиг из примера:
   ```java
   cp carwash.properties.example ~/.carwash.properties
   ```
   В `~/.carwash.properties` укажи пароль, который ввёл для пользователя `carwash`.
   Параметры также можно переопределить переменными `CARWASH_DB_URL`, `CARWASH_DB_USER` и `CARWASH_DB_PASSWORD`.
4. Запустить программу и выбрать пункт меню **7**, чтобы создать таблицы и тестовые данные. Этот пункт удаляет существующие таблицы перед созданием.

### Как запустить

### В IntelliJ IDEA
1. Открыть папку проекта (File → Open).
2. При запросе JDK выбрать 17 или новее.
3. Дождаться, пока Maven скачает библиотеки (драйвер PostgreSQL, Apache POI).
4. Запустить `src/main/java/carwash/Main.java` (зелёная стрелка ▶).

### Через Maven
```
mvn clean package
java -jar target/carwash.jar
```
Если в терминале Windows кириллица отображается неправильно, перед запуском:
```
chcp 65001
```

### Структура проекта
```
src/main/java/carwash/
├── Main.java              — точка входа
├── model/                 — Client, Booking, ServiceType, BookingStatus
├── repository/            — работа с БД через JDBC
├── service/               — бизнес-логика и проверки
├── ui/                    — консольное меню и ввод
├── util/                  — подключение к БД, экспорт
└── exception/             — свои исключения
src/main/resources/schema.sql   — создание таблиц и тестовые данные
pom.xml                         — зависимости и сборка
```

### База данных
Две связанные таблицы:
- `clients` — клиенты (id, имя, телефон, email);
- `bookings` — записи на мойку (id, client_id → clients.id, авто, услуга,
  статус, дата, цена).

Ограничения: PRIMARY KEY, FOREIGN KEY, NOT NULL, UNIQUE, CHECK.