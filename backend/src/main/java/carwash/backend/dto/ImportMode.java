package carwash.backend.dto;

// режимы импорта CSV
public enum ImportMode {
    // только новые записи: строка с уже существующим кодом считается ошибкой
    CREATE_ONLY,
    // обновление по внешнему идентификатору (code): есть — изменить, нет — создать
    UPSERT
}
