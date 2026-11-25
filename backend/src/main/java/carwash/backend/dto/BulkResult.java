package carwash.backend.dto;

import java.util.List;
import java.util.UUID;

// результат массовой операции: отдельно успешные и ошибочные записи
public record BulkResult(
        BulkAction action,
        int requested,
        int succeededCount,
        int failedCount,
        List<UUID> succeeded,
        List<BulkError> failed) {

    public record BulkError(UUID id, String error, String message) {
    }
}
