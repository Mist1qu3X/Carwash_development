package carwash.backend.controller;

import carwash.backend.config.AppProperties;
import carwash.backend.dto.BookingRequest;
import carwash.backend.dto.BookingResponse;
import carwash.backend.dto.BulkRequest;
import carwash.backend.dto.BulkResult;
import carwash.backend.dto.ImportMode;
import carwash.backend.dto.ImportReport;
import carwash.backend.dto.PageResponse;
import carwash.backend.filter.BookingFilter;
import carwash.backend.filter.PageRequestFactory;
import carwash.backend.filter.SortFields;
import carwash.backend.service.BookingBulkService;
import carwash.backend.service.BookingCsvService;
import carwash.backend.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// записи на мойку — основная сущность (в задании условно /api/items)
@RestController
@RequestMapping("/api/bookings")
@Tag(name = "Записи на мойку")
public class BookingController {

    private final BookingService bookingService;
    private final BookingBulkService bulkService;
    private final BookingCsvService csvService;
    private final AppProperties properties;
    private final Clock clock;

    public BookingController(BookingService bookingService, BookingBulkService bulkService,
                             BookingCsvService csvService, AppProperties properties, Clock clock) {
        this.bookingService = bookingService;
        this.bulkService = bulkService;
        this.csvService = csvService;
        this.properties = properties;
        this.clock = clock;
    }

    @GetMapping
    @PreAuthorize("hasRole('VIEWER')")
    @Operation(summary = "Список записей: поиск, фильтры, сортировка, пагинация")
    public PageResponse<BookingResponse> list(
            @ParameterObject BookingFilter filter,
            @Parameter(description = "Номер страницы с 0") @RequestParam(required = false) Integer page,
            @Parameter(description = "Размер страницы (максимум 100)") @RequestParam(required = false) Integer size,
            @Parameter(description = "Сортировка: поле,asc|desc; поля: scheduledAt, price, code, status, carNumber, "
                    + "clientName, serviceType, createdAt, updatedAt") @RequestParam(required = false) String sort) {
        return bookingService.search(filter, PageRequestFactory.create(page, size, sort,
                SortFields.BOOKINGS, SortFields.BOOKINGS_DEFAULT, properties.bookings().maxPageSize()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('VIEWER')")
    @Operation(summary = "Карточка записи")
    public BookingResponse get(@PathVariable UUID id) {
        return bookingService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Создание записи")
    public BookingResponse create(@Valid @RequestBody BookingRequest request) {
        return bookingService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Изменение записи (обязателен version, иначе 409 при конфликте)")
    public BookingResponse update(@PathVariable UUID id, @Valid @RequestBody BookingRequest request) {
        return bookingService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Архивирование записи (вместо удаления)")
    public BookingResponse archive(@PathVariable UUID id) {
        return bookingService.archive(id);
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Восстановление из архива")
    public BookingResponse restore(@PathVariable UUID id) {
        return bookingService.restore(id);
    }

    @DeleteMapping("/{id}/permanent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Окончательное удаление архивной записи (только администратор)")
    public void deletePermanently(@PathVariable UUID id) {
        bookingService.deletePermanently(id);
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Массовая операция: ARCHIVE, RESTORE, CHANGE_STATUS, ADD_TAG, REMOVE_TAG")
    public BulkResult bulk(@Valid @RequestBody BulkRequest request) {
        return bulkService.execute(request);
    }

    @GetMapping("/tags")
    @PreAuthorize("hasRole('VIEWER')")
    @Operation(summary = "Все используемые теги (для фильтра)")
    public List<String> tags() {
        return bookingService.allTags();
    }

    @GetMapping("/export")
    @PreAuthorize("hasRole('VIEWER')")
    @Operation(summary = "Экспорт в CSV с учётом текущего фильтра и сортировки")
    public ResponseEntity<byte[]> export(@ParameterObject BookingFilter filter,
                                         @RequestParam(required = false) String sort) {
        Sort parsed = PageRequestFactory.parseSort(sort, SortFields.BOOKINGS);
        byte[] csv = csvService.export(filter, parsed.isSorted() ? parsed : SortFields.BOOKINGS_DEFAULT);
        String fileName = "bookings-" + LocalDate.now(clock) + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv);
    }

    @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Предварительная проверка CSV (база не изменяется)")
    public ImportReport importPreview(@RequestPart("file") MultipartFile file,
                                      @RequestParam(defaultValue = "CREATE_ONLY") ImportMode mode) {
        return csvService.preview(file, mode);
    }

    @PostMapping(value = "/import/apply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Применение импорта CSV: корректные строки сохраняются, ошибочные пропускаются")
    public ImportReport importApply(@RequestPart("file") MultipartFile file,
                                    @RequestParam(defaultValue = "CREATE_ONLY") ImportMode mode) {
        return csvService.apply(file, mode);
    }
}
