package carwash.backend.service;

import carwash.backend.domain.AuditEventType;
import carwash.backend.dto.BulkAction;
import carwash.backend.dto.BulkRequest;
import carwash.backend.dto.BulkResult;
import carwash.backend.error.ApiException;
import carwash.backend.error.BadRequestException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Массовые операции над набором записей.
 * Класс намеренно без @Transactional: каждая запись обрабатывается в своей транзакции
 * (через методы BookingService), поэтому ошибка по одной записи не отменяет остальные.
 */
@Service
public class BookingBulkService {

    private final BookingService bookingService;
    private final AuditService audit;

    public BookingBulkService(BookingService bookingService, AuditService audit) {
        this.bookingService = bookingService;
        this.audit = audit;
    }

    public BulkResult execute(BulkRequest request) {
        BulkAction action = request.action();
        if (action == BulkAction.CHANGE_STATUS && request.status() == null) {
            throw new BadRequestException("STATUS_REQUIRED", "Для смены статуса укажите status");
        }
        String tag = null;
        if (action == BulkAction.ADD_TAG || action == BulkAction.REMOVE_TAG) {
            tag = BookingService.normalizeTag(request.tag());
        }

        List<UUID> ids = request.ids().stream().distinct().toList();
        List<UUID> succeeded = new ArrayList<>();
        List<BulkResult.BulkError> failed = new ArrayList<>();
        for (UUID id : ids) {
            try {
                switch (action) {
                    case ARCHIVE -> bookingService.archive(id);
                    case RESTORE -> bookingService.restore(id);
                    case CHANGE_STATUS -> bookingService.changeStatus(id, request.status());
                    case ADD_TAG -> bookingService.addTag(id, tag);
                    case REMOVE_TAG -> bookingService.removeTag(id, tag);
                }
                succeeded.add(id);
            } catch (ApiException e) {
                failed.add(new BulkResult.BulkError(id, e.getCode(), e.getMessage()));
            } catch (ObjectOptimisticLockingFailureException e) {
                failed.add(new BulkResult.BulkError(id, "VERSION_CONFLICT", "Запись одновременно изменил другой пользователь"));
            }
        }

        String details = switch (action) {
            case CHANGE_STATUS -> " (статус " + request.status().getTitle() + ")";
            case ADD_TAG, REMOVE_TAG -> " (тег «" + tag + "»)";
            default -> "";
        };
        audit.log(AuditEventType.BOOKING_BULK, EntityTypes.BOOKING, null,
                "Массовая операция " + action + details + ": выбрано " + ids.size()
                        + ", успешно " + succeeded.size() + ", ошибок " + failed.size());
        return new BulkResult(action, ids.size(), succeeded.size(), failed.size(), succeeded, failed);
    }
}
