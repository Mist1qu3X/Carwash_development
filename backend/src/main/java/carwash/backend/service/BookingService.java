package carwash.backend.service;

import carwash.backend.config.AppProperties;
import carwash.backend.domain.AuditEventType;
import carwash.backend.domain.Booking;
import carwash.backend.domain.BookingStatus;
import carwash.backend.domain.Client;
import carwash.backend.domain.ServiceType;
import carwash.backend.dto.BookingRequest;
import carwash.backend.dto.BookingResponse;
import carwash.backend.dto.ImportMode;
import carwash.backend.dto.ImportReport;
import carwash.backend.dto.PageResponse;
import carwash.backend.error.BadRequestException;
import carwash.backend.error.BusinessException;
import carwash.backend.error.ConflictException;
import carwash.backend.error.ForbiddenException;
import carwash.backend.error.NotFoundException;
import carwash.backend.filter.BookingFilter;
import carwash.backend.filter.BookingSpecifications;
import carwash.backend.repository.BookingRepository;
import carwash.backend.repository.ClientRepository;
import carwash.backend.repository.ServiceTypeRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Бизнес-логика записей на мойку. Правила из КР1 сохранены:
 * переходы статусов, дата новой записи в будущем, запись "в работе" нельзя убрать.
 */
@Service
public class BookingService {

    public static final int MAX_TAGS = 10;

    private final BookingRepository bookings;
    private final ClientRepository clients;
    private final ServiceTypeRepository serviceTypes;
    private final AuditService audit;
    private final AppProperties properties;
    private final Clock clock;

    public BookingService(BookingRepository bookings, ClientRepository clients, ServiceTypeRepository serviceTypes,
                          AuditService audit, AppProperties properties, Clock clock) {
        this.bookings = bookings;
        this.clients = clients;
        this.serviceTypes = serviceTypes;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
    }

    // ---------- чтение ----------

    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> search(BookingFilter filter, Pageable pageable) {
        return PageResponse.of(bookings.findAll(BookingSpecifications.fromFilter(filter), pageable), BookingResponse::from);
    }

    @Transactional(readOnly = true)
    public BookingResponse get(UUID id) {
        return BookingResponse.from(load(id));
    }

    @Transactional(readOnly = true)
    public List<String> allTags() {
        return bookings.findAllTags();
    }

    // ---------- создание и изменение ----------

    @Transactional
    public BookingResponse create(BookingRequest request) {
        Booking booking = createInternal(request);
        audit.log(AuditEventType.BOOKING_CREATED, EntityTypes.BOOKING, booking.getId(),
                "Создана запись " + booking.getCode() + " (" + booking.getCarNumber() + ", "
                        + booking.getServiceType().getName() + ")");
        return BookingResponse.from(booking);
    }

    @Transactional
    public BookingResponse update(UUID id, BookingRequest request) {
        if (request.version() == null) {
            throw new BadRequestException("VERSION_REQUIRED", "Для изменения записи нужно передать version");
        }
        Booking booking = load(id);
        if (!Objects.equals(booking.getVersion(), request.version())) {
            throw ConflictException.version(booking.getVersion());
        }
        String changes = applyUpdate(booking, request);
        bookings.flush();
        audit.log(AuditEventType.BOOKING_UPDATED, EntityTypes.BOOKING, booking.getId(),
                "Изменена запись " + booking.getCode() + changes);
        return BookingResponse.from(booking);
    }

    /**
     * Одна строка импорта CSV. Вызывается из BookingCsvService внутри его транзакции
     * (при предварительной проверке эта транзакция потом откатывается).
     */
    @Transactional
    public ImportReport.RowAction importRow(BookingRequest request, ImportMode mode) {
        if (request.code() != null) {
            Optional<Booking> existing = bookings.findByCode(normalizeCode(request.code()));
            if (existing.isPresent()) {
                if (mode == ImportMode.CREATE_ONLY) {
                    throw new ConflictException("DUPLICATE_CODE",
                            "Запись с кодом " + existing.get().getCode() + " уже существует (режим «только новые»)");
                }
                Booking booking = existing.get();
                String changes = applyUpdate(booking, request);
                audit.log(AuditEventType.BOOKING_UPDATED, EntityTypes.BOOKING, booking.getId(),
                        "Изменена при импорте CSV запись " + booking.getCode() + changes);
                return ImportReport.RowAction.UPDATE;
            }
        }
        Booking booking = createInternal(request);
        audit.log(AuditEventType.BOOKING_CREATED, EntityTypes.BOOKING, booking.getId(),
                "Создана при импорте CSV запись " + booking.getCode());
        return ImportReport.RowAction.CREATE;
    }

    private Booking createInternal(BookingRequest request) {
        BookingStatus status = request.status() == null ? BookingStatus.CREATED : request.status();
        LocalDateTime scheduledAt = request.scheduledAt().truncatedTo(ChronoUnit.MINUTES);
        // правило КР1: новая запланированная запись не может быть в прошлом
        if (status.isPlanned() && scheduledAt.isBefore(LocalDateTime.now(clock))) {
            throw new BusinessException("DATE_IN_PAST", "Дата записи должна быть в будущем");
        }
        Booking booking = new Booking();
        booking.setCode(resolveNewCode(request.code()));
        booking.setClient(findClient(request.clientId()));
        booking.setServiceType(findActiveServiceType(request.serviceTypeId()));
        booking.setStatus(status);
        fillCommonFields(booking, request);
        return bookings.save(booking);
    }

    // проверяет правила изменения и переносит поля; возвращает краткое описание изменений для аудита
    private String applyUpdate(Booking booking, BookingRequest request) {
        if (booking.isArchived()) {
            throw new ConflictException("BOOKING_ARCHIVED", "Запись в архиве. Восстановите её перед изменением");
        }
        BookingStatus oldStatus = booking.getStatus();
        BookingStatus newStatus = request.status() == null ? oldStatus : request.status();
        if (!oldStatus.canChangeTo(newStatus)) {
            throw new BusinessException("INVALID_STATUS_TRANSITION",
                    "Недопустимый переход статуса: " + oldStatus.getTitle() + " → " + newStatus.getTitle());
        }
        Client client = booking.getClient().getId().equals(request.clientId())
                ? booking.getClient() : findClient(request.clientId());
        // услугу, которую потом отключили, у старой записи оставить можно, а выбрать заново — нельзя
        ServiceType serviceType = booking.getServiceType().getId().equals(request.serviceTypeId())
                ? booking.getServiceType() : findActiveServiceType(request.serviceTypeId());

        if (oldStatus.isFinal() && coreFieldsChanged(booking, request, client, serviceType)) {
            throw new BusinessException("FINAL_STATUS_LOCKED",
                    "У записи со статусом «" + oldStatus.getTitle()
                            + "» можно менять только оплату, теги и комментарий");
        }
        if (request.code() != null) {
            String code = normalizeCode(request.code());
            if (!code.equals(booking.getCode())) {
                if (bookings.existsByCode(code)) {
                    throw new ConflictException("DUPLICATE_CODE", "Запись с кодом " + code + " уже существует");
                }
                booking.setCode(code);
            }
        }

        StringBuilder changes = new StringBuilder();
        if (oldStatus != newStatus) {
            changes.append(", статус: ").append(oldStatus.getTitle()).append(" → ").append(newStatus.getTitle());
        }
        if (booking.isPaid() != request.paid()) {
            changes.append(request.paid() ? ", отмечена оплата" : ", снята отметка об оплате");
        }
        if (booking.getPrice().compareTo(request.price()) != 0) {
            changes.append(", цена: ").append(booking.getPrice()).append(" → ").append(request.price());
        }

        booking.setClient(client);
        booking.setServiceType(serviceType);
        booking.setStatus(newStatus);
        fillCommonFields(booking, request);
        return changes.toString();
    }

    private void fillCommonFields(Booking booking, BookingRequest request) {
        booking.setCarNumber(normalizeCarNumber(request.carNumber()));
        booking.setCarModel(blankToNull(request.carModel()));
        booking.setCustomerComment(blankToNull(request.customerComment()));
        booking.setScheduledAt(request.scheduledAt().truncatedTo(ChronoUnit.MINUTES));
        booking.setPrice(request.price().setScale(2, RoundingMode.HALF_UP));
        booking.setPaid(request.paid());
        booking.replaceTags(normalizeTags(request.tags()));
    }

    private boolean coreFieldsChanged(Booking booking, BookingRequest request, Client client, ServiceType serviceType) {
        return !booking.getClient().getId().equals(client.getId())
                || !booking.getServiceType().getId().equals(serviceType.getId())
                || !booking.getCarNumber().equals(normalizeCarNumber(request.carNumber()))
                || !Objects.equals(booking.getCarModel(), blankToNull(request.carModel()))
                || !booking.getScheduledAt().equals(request.scheduledAt().truncatedTo(ChronoUnit.MINUTES))
                || booking.getPrice().compareTo(request.price()) != 0;
    }

    // ---------- архив и удаление ----------

    @Transactional
    public BookingResponse archive(UUID id) {
        Booking booking = load(id);
        if (booking.isArchived()) {
            throw new ConflictException("ALREADY_ARCHIVED", "Запись " + booking.getCode() + " уже в архиве");
        }
        // правило КР1: запись, которая находится в работе, убирать нельзя
        if (booking.getStatus() == BookingStatus.IN_PROGRESS) {
            throw new BusinessException("IN_PROGRESS", "Нельзя архивировать запись, которая находится в работе");
        }
        booking.archive(clock.instant());
        bookings.flush();
        audit.log(AuditEventType.BOOKING_ARCHIVED, EntityTypes.BOOKING, booking.getId(),
                "Запись " + booking.getCode() + " перемещена в архив");
        return BookingResponse.from(booking);
    }

    @Transactional
    public BookingResponse restore(UUID id) {
        Booking booking = load(id);
        if (!booking.isArchived()) {
            throw new ConflictException("NOT_ARCHIVED", "Запись " + booking.getCode() + " не находится в архиве");
        }
        booking.restore();
        bookings.flush();
        audit.log(AuditEventType.BOOKING_RESTORED, EntityTypes.BOOKING, booking.getId(),
                "Запись " + booking.getCode() + " восстановлена из архива");
        return BookingResponse.from(booking);
    }

    // окончательное удаление: только ADMIN (проверка в контроллере), только из архива и если разрешено в .env
    @Transactional
    public void deletePermanently(UUID id) {
        if (!properties.bookings().permanentDeleteEnabled()) {
            throw new ForbiddenException("PERMANENT_DELETE_DISABLED",
                    "Окончательное удаление отключено настройкой PERMANENT_DELETE_ENABLED");
        }
        Booking booking = load(id);
        if (!booking.isArchived()) {
            throw new ConflictException("NOT_ARCHIVED", "Сначала переместите запись в архив");
        }
        String code = booking.getCode();
        bookings.delete(booking);
        bookings.flush();
        audit.log(AuditEventType.BOOKING_DELETED, EntityTypes.BOOKING, id, "Запись " + code + " удалена окончательно");
    }

    // ---------- операции для массовой обработки ----------

    @Transactional
    public BookingResponse changeStatus(UUID id, BookingStatus status) {
        Booking booking = loadEditable(id);
        BookingStatus old = booking.getStatus();
        if (old == status) {
            return BookingResponse.from(booking);
        }
        if (!old.canChangeTo(status)) {
            throw new BusinessException("INVALID_STATUS_TRANSITION",
                    "Недопустимый переход статуса: " + old.getTitle() + " → " + status.getTitle());
        }
        booking.setStatus(status);
        bookings.flush();
        audit.log(AuditEventType.BOOKING_STATUS_CHANGED, EntityTypes.BOOKING, booking.getId(),
                "Запись " + booking.getCode() + ": статус " + old.getTitle() + " → " + status.getTitle());
        return BookingResponse.from(booking);
    }

    @Transactional
    public BookingResponse addTag(UUID id, String rawTag) {
        Booking booking = loadEditable(id);
        String tag = normalizeTag(rawTag);
        if (!booking.getTags().contains(tag) && booking.getTags().size() >= MAX_TAGS) {
            throw new BusinessException("TOO_MANY_TAGS", "У записи не может быть больше " + MAX_TAGS + " тегов");
        }
        if (booking.addTag(tag)) {
            bookings.flush();
            audit.log(AuditEventType.BOOKING_TAGS_CHANGED, EntityTypes.BOOKING, booking.getId(),
                    "Запись " + booking.getCode() + ": добавлен тег «" + tag + "»");
        }
        return BookingResponse.from(booking);
    }

    @Transactional
    public BookingResponse removeTag(UUID id, String rawTag) {
        Booking booking = loadEditable(id);
        String tag = normalizeTag(rawTag);
        if (booking.removeTag(tag)) {
            bookings.flush();
            audit.log(AuditEventType.BOOKING_TAGS_CHANGED, EntityTypes.BOOKING, booking.getId(),
                    "Запись " + booking.getCode() + ": удалён тег «" + tag + "»");
        }
        return BookingResponse.from(booking);
    }

    // ---------- вспомогательные методы ----------

    public Booking load(UUID id) {
        return bookings.findWithDetailsById(id).orElseThrow(() -> new NotFoundException("Запись не найдена"));
    }

    private Booking loadEditable(UUID id) {
        Booking booking = load(id);
        if (booking.isArchived()) {
            throw new ConflictException("BOOKING_ARCHIVED", "Запись " + booking.getCode() + " в архиве");
        }
        return booking;
    }

    private Client findClient(UUID id) {
        return clients.findById(id)
                .orElseThrow(() -> new BusinessException("CLIENT_NOT_FOUND", "Клиент не найден"));
    }

    private ServiceType findActiveServiceType(UUID id) {
        ServiceType type = serviceTypes.findById(id)
                .orElseThrow(() -> new BusinessException("SERVICE_TYPE_NOT_FOUND", "Вид услуги не найден"));
        if (!type.isActive()) {
            throw new BusinessException("SERVICE_TYPE_DISABLED", "Услуга «" + type.getName() + "» отключена и недоступна для записи");
        }
        return type;
    }

    private String resolveNewCode(String requested) {
        if (requested != null && !requested.isBlank()) {
            String code = normalizeCode(requested);
            if (bookings.existsByCode(code)) {
                throw new ConflictException("DUPLICATE_CODE", "Запись с кодом " + code + " уже существует");
            }
            return code;
        }
        String code;
        do {
            code = String.format("CW-%06d", bookings.nextCodeNumber());
        } while (bookings.existsByCode(code));
        return code;
    }

    public static String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    // гос. номер храним в верхнем регистре без лишних пробелов: "а 123 вс77" -> "А123ВС77"
    public static String normalizeCarNumber(String carNumber) {
        return carNumber.trim().replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    public static String normalizeTag(String tag) {
        if (tag == null || tag.isBlank()) {
            throw new BadRequestException("TAG_REQUIRED", "Укажите тег");
        }
        String value = tag.trim().toLowerCase(Locale.ROOT);
        if (!value.matches("^[\\p{L}0-9_-]{1,30}$")) {
            throw new BadRequestException("BAD_TAG", "Тег: 1–30 символов, буквы, цифры, '-' и '_'");
        }
        return value;
    }

    private static Set<String> normalizeTags(Collection<String> tags) {
        if (tags == null) {
            return Set.of();
        }
        Set<String> result = tags.stream().map(BookingService::normalizeTag).collect(Collectors.toSet());
        if (result.size() > MAX_TAGS) {
            throw new BusinessException("TOO_MANY_TAGS", "У записи не может быть больше " + MAX_TAGS + " тегов");
        }
        return result;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
