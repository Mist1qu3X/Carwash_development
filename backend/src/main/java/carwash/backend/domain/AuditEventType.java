package carwash.backend.domain;

// типы событий журнала аудита
public enum AuditEventType {

    LOGIN_SUCCESS("Успешный вход"),
    LOGIN_FAILED("Неудачный вход"),
    LOGIN_LOCKED("Блокировка после неудачных попыток"),
    LOGIN_BLOCKED("Вход в заблокированную учётную запись"),
    LOGOUT("Выход"),
    TOKEN_REUSE("Повторное использование refresh-токена"),
    PASSWORD_CHANGED("Смена пароля"),
    PASSWORD_RESET("Сброс пароля администратором"),
    USER_CREATED("Создание пользователя"),
    USER_UPDATED("Изменение пользователя"),
    BOOKING_CREATED("Создание записи"),
    BOOKING_UPDATED("Изменение записи"),
    BOOKING_STATUS_CHANGED("Смена статуса записи"),
    BOOKING_TAGS_CHANGED("Изменение тегов записи"),
    BOOKING_ARCHIVED("Архивирование записи"),
    BOOKING_RESTORED("Восстановление записи"),
    BOOKING_DELETED("Окончательное удаление записи"),
    BOOKING_BULK("Массовая операция"),
    BOOKING_IMPORTED("Импорт CSV"),
    BOOKING_EXPORTED("Экспорт CSV"),
    NOTE_CREATED("Создание заметки"),
    NOTE_UPDATED("Изменение заметки"),
    NOTE_DELETED("Удаление заметки"),
    CLIENT_CREATED("Создание клиента"),
    CLIENT_UPDATED("Изменение клиента"),
    SERVICE_TYPE_CREATED("Создание услуги"),
    SERVICE_TYPE_UPDATED("Изменение услуги"),
    SERVICE_TYPE_ENABLED("Включение услуги"),
    SERVICE_TYPE_DISABLED("Отключение услуги");

    private final String title;

    AuditEventType(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}
