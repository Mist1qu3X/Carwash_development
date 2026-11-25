package carwash.backend.service;

import carwash.backend.config.AppProperties;
import carwash.backend.domain.AuditEventType;
import carwash.backend.domain.Booking;
import carwash.backend.domain.BookingStatus;
import carwash.backend.domain.ServiceType;
import carwash.backend.dto.BookingRequest;
import carwash.backend.dto.ClientRequest;
import carwash.backend.dto.ImportMode;
import carwash.backend.dto.ImportReport;
import carwash.backend.error.ApiException;
import carwash.backend.error.BadRequestException;
import carwash.backend.filter.BookingFilter;
import carwash.backend.filter.BookingSpecifications;
import carwash.backend.repository.BookingRepository;
import carwash.backend.repository.ClientRepository;
import carwash.backend.repository.ServiceTypeRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Экспорт и импорт записей в CSV (разделитель ';', UTF-8 с BOM — Excel открывает по столбцам).
 *
 * Импорт выполняется в два запроса:
 *  1) preview — каждая строка проходит ровно те же проверки, что и при применении,
 *     но транзакция строки откатывается, поэтому в базе ничего не меняется;
 *  2) apply — то же самое, но транзакция каждой корректной строки фиксируется.
 * Каждая строка обрабатывается в своей транзакции: ошибка в одной строке не мешает другим.
 */
@Service
public class BookingCsvService {

    private static final Logger log = LoggerFactory.getLogger(BookingCsvService.class);

    public static final List<String> HEADERS = List.of(
            "code", "clientName", "clientPhone", "carNumber", "carModel", "serviceType",
            "status", "scheduledAt", "price", "paid", "tags", "comment", "paymentRisk", "archived");
    private static final List<String> REQUIRED_HEADERS = List.of("clientPhone", "carNumber", "serviceType", "scheduledAt");

    private static final DateTimeFormatter CSV_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final List<DateTimeFormatter> INPUT_DATES = List.of(
            CSV_DATE,
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")); // формат из КР1
    private static final int MAX_ROWS_IN_REPORT = 1000;

    private final BookingRepository bookings;
    private final BookingService bookingService;
    private final ClientService clientService;
    private final ClientRepository clients;
    private final ServiceTypeRepository serviceTypes;
    private final Validator validator;
    private final TransactionTemplate rowTransaction;
    private final AuditService audit;
    private final AppProperties properties;

    public BookingCsvService(BookingRepository bookings, BookingService bookingService, ClientService clientService,
                             ClientRepository clients, ServiceTypeRepository serviceTypes, Validator validator,
                             PlatformTransactionManager transactionManager, AuditService audit,
                             AppProperties properties) {
        this.bookings = bookings;
        this.bookingService = bookingService;
        this.clientService = clientService;
        this.clients = clients;
        this.serviceTypes = serviceTypes;
        this.validator = validator;
        this.rowTransaction = new TransactionTemplate(transactionManager);
        this.audit = audit;
        this.properties = properties;
    }

    // ===================== ЭКСПОРТ =====================

    @Transactional
    public byte[] export(BookingFilter filter, Sort sort) {
        int limit = properties.bookings().exportMaxRows();
        Page<Booking> page = bookings.findAll(BookingSpecifications.fromFilter(filter), PageRequest.of(0, limit, sort));

        StringBuilder out = new StringBuilder("﻿");
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(';')
                .setHeader(HEADERS.toArray(String[]::new))
                .setRecordSeparator("\r\n")
                .get();
        try (CSVPrinter printer = new CSVPrinter(out, format)) {
            for (Booking b : page) {
                printer.printRecord(
                        b.getCode(),
                        sanitize(b.getClient().getFullName()),
                        sanitize(b.getClient().getPhone()),
                        sanitize(b.getCarNumber()),
                        sanitize(b.getCarModel()),
                        b.getServiceType().getCode(),
                        b.getStatus().name(),
                        CSV_DATE.format(b.getScheduledAt()),
                        b.getPrice().toPlainString(),
                        b.isPaid(),
                        b.getTags().stream().sorted().map(BookingCsvService::sanitize).collect(Collectors.joining(",")),
                        sanitize(b.getCustomerComment()),
                        b.getPaymentRisk().name(),
                        b.isArchived());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String limited = page.getTotalElements() > limit ? " (ограничение " + limit + " строк)" : "";
        audit.log(AuditEventType.BOOKING_EXPORTED, EntityTypes.BOOKING, null,
                "Экспорт CSV: " + page.getNumberOfElements() + " записей" + limited);
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    // защита от CSV-инъекций: Excel не должен выполнять значения вида =HYPERLINK(...)
    static String sanitize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            return "'" + value;
        }
        return value;
    }

    static String desanitize(String value) {
        if (value != null && value.length() > 1 && value.charAt(0) == '\'' && "=+-@\t\r".indexOf(value.charAt(1)) >= 0) {
            return value.substring(1);
        }
        return value;
    }

    // ===================== ИМПОРТ =====================

    public ImportReport preview(MultipartFile file, ImportMode mode) {
        return process(file, mode, true);
    }

    public ImportReport apply(MultipartFile file, ImportMode mode) {
        ImportReport report = process(file, mode, false);
        audit.log(AuditEventType.BOOKING_IMPORTED, EntityTypes.BOOKING, null,
                "Импорт CSV «" + file.getOriginalFilename() + "» (" + mode + "): строк " + report.totalRows()
                        + ", создано " + report.created() + ", обновлено " + report.updated()
                        + ", ошибок " + report.invalidRows());
        return report;
    }

    private ImportReport process(MultipartFile file, ImportMode mode, boolean dryRun) {
        List<CSVRecord> records = parse(file);
        Set<String> seenCodes = new HashSet<>();
        List<ImportReport.RowResult> rows = new ArrayList<>();
        int valid = 0;
        int invalid = 0;
        int created = 0;
        int updated = 0;

        for (CSVRecord record : records) {
            long line = record.getRecordNumber() + 1; // +1 — строка заголовка
            String code = blankToNull(value(record, "code"));
            ImportReport.RowResult result;
            try {
                if (code != null && !seenCodes.add(BookingService.normalizeCode(code))) {
                    throw new RowException("Код " + code + " повторяется в файле");
                }
                ImportReport.RowAction action = rowTransaction.execute(status -> {
                    ImportReport.RowAction rowAction = importRecord(record, mode);
                    if (dryRun) {
                        bookings.flush();          // проверяем и ограничения БД
                        status.setRollbackOnly();  // ...но ничего не сохраняем
                    }
                    return rowAction;
                });
                valid++;
                if (action == ImportReport.RowAction.CREATE) {
                    created++;
                } else {
                    updated++;
                }
                result = new ImportReport.RowResult(line, code, action, describe(action, dryRun));
            } catch (RowException | ApiException e) {
                invalid++;
                result = new ImportReport.RowResult(line, code, ImportReport.RowAction.ERROR, e.getMessage());
            } catch (DataIntegrityViolationException e) {
                invalid++;
                result = new ImportReport.RowResult(line, code, ImportReport.RowAction.ERROR,
                        "Строка нарушает ограничения базы данных");
            } catch (ObjectOptimisticLockingFailureException e) {
                invalid++;
                result = new ImportReport.RowResult(line, code, ImportReport.RowAction.ERROR,
                        "Запись одновременно изменил другой пользователь");
            } catch (RuntimeException e) {
                log.warn("Ошибка обработки строки {} CSV", line, e);
                invalid++;
                result = new ImportReport.RowResult(line, code, ImportReport.RowAction.ERROR, "Не удалось обработать строку");
            }
            if (rows.size() < MAX_ROWS_IN_REPORT) {
                rows.add(result);
            }
        }
        return new ImportReport(mode, dryRun, records.size(), valid, invalid, created, updated, rows,
                records.size() > MAX_ROWS_IN_REPORT);
    }

    // разбор одной строки -> BookingRequest -> те же проверки, что у REST API
    private ImportReport.RowAction importRecord(CSVRecord record, ImportMode mode) {
        List<String> errors = new ArrayList<>();

        String code = blankToNull(value(record, "code"));
        String phone = blankToNull(desanitize(value(record, "clientPhone")));
        String clientName = blankToNull(desanitize(value(record, "clientName")));
        String carNumber = blankToNull(desanitize(value(record, "carNumber")));
        String carModel = blankToNull(desanitize(value(record, "carModel")));
        String comment = blankToNull(desanitize(value(record, "comment")));

        if (phone == null) {
            errors.add("Не указан телефон клиента (clientPhone)");
        }
        ServiceType serviceType = findServiceType(blankToNull(value(record, "serviceType")), errors);
        BookingStatus status = parseStatus(blankToNull(value(record, "status")), errors);
        LocalDateTime scheduledAt = parseDate(blankToNull(value(record, "scheduledAt")), errors);
        BigDecimal price = parsePrice(blankToNull(value(record, "price")), serviceType, errors);
        boolean paid = parseBoolean(blankToNull(value(record, "paid")), errors);
        Set<String> tags = parseTags(value(record, "tags"));

        if (!errors.isEmpty()) {
            throw new RowException(String.join("; ", errors));
        }

        UUID clientId = resolveClient(phone, clientName);
        BookingRequest request = new BookingRequest(code, clientId, carNumber, carModel, comment,
                serviceType.getId(), status, scheduledAt, price, paid, tags, null);
        checkConstraints(request);
        return bookingService.importRow(request, mode);
    }

    // клиент ищется по телефону; если его нет и указано имя — создаётся новый клиент
    private UUID resolveClient(String phone, String clientName) {
        Optional<UUID> existing = clients.findByPhone(phone.trim()).map(c -> c.getId());
        if (existing.isPresent()) {
            return existing.get();
        }
        if (clientName == null) {
            throw new RowException("Клиент с телефоном " + phone + " не найден, а имя клиента (clientName) не указано");
        }
        ClientRequest clientRequest = new ClientRequest(clientName, phone, null, null);
        checkConstraints(clientRequest);
        return clientService.create(clientRequest).id();
    }

    private <T> void checkConstraints(T object) {
        Set<ConstraintViolation<T>> violations = validator.validate(object);
        if (!violations.isEmpty()) {
            throw new RowException(violations.stream()
                    .map(ConstraintViolation::getMessage)
                    .sorted()
                    .collect(Collectors.joining("; ")));
        }
    }

    private ServiceType findServiceType(String value, List<String> errors) {
        if (value == null) {
            errors.add("Не указан вид услуги (serviceType)");
            return null;
        }
        Optional<ServiceType> type = serviceTypes.findByCode(value.toUpperCase(Locale.ROOT))
                .or(() -> serviceTypes.findByNameIgnoreCase(value));
        if (type.isEmpty()) {
            errors.add("Вид услуги «" + value + "» не найден в справочнике");
            return null;
        }
        return type.get();
    }

    private static BookingStatus parseStatus(String value, List<String> errors) {
        if (value == null) {
            return null; // новая запись -> CREATED, существующая -> статус не меняется
        }
        for (BookingStatus status : BookingStatus.values()) {
            if (status.name().equalsIgnoreCase(value) || status.getTitle().equalsIgnoreCase(value)) {
                return status;
            }
        }
        errors.add("Неизвестный статус «" + value + "»");
        return null;
    }

    private static LocalDateTime parseDate(String value, List<String> errors) {
        if (value == null) {
            errors.add("Не указана дата записи (scheduledAt)");
            return null;
        }
        for (DateTimeFormatter formatter : INPUT_DATES) {
            try {
                return LocalDateTime.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // пробуем следующий формат
            }
        }
        errors.add("Дата «" + value + "» не распознана (ожидается 2026-12-01 10:30)");
        return null;
    }

    private static BigDecimal parsePrice(String value, ServiceType serviceType, List<String> errors) {
        if (value == null) {
            // цена не указана — берём базовую цену услуги из справочника
            return serviceType == null ? null : serviceType.getBasePrice();
        }
        try {
            return new BigDecimal(value.replace(" ", "").replace(" ", "").replace(',', '.'));
        } catch (NumberFormatException e) {
            errors.add("Цена «" + value + "» не является числом");
            return null;
        }
    }

    private static boolean parseBoolean(String value, List<String> errors) {
        if (value == null) {
            return false;
        }
        String v = value.toLowerCase(Locale.ROOT);
        if (Set.of("true", "1", "yes", "да", "y").contains(v)) {
            return true;
        }
        if (Set.of("false", "0", "no", "нет", "n").contains(v)) {
            return false;
        }
        errors.add("Признак оплаты «" + value + "» должен быть true/false или да/нет");
        return false;
    }

    private static Set<String> parseTags(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split("[,|]"))
                .map(BookingCsvService::desanitize)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<CSVRecord> parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("EMPTY_FILE", "Файл не выбран или пустой");
        }
        String text;
        try {
            text = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BadRequestException("BAD_FILE", "Не удалось прочитать файл");
        }
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        String firstLine = text.lines().findFirst().orElse("");
        char delimiter = firstLine.contains(";") ? ';' : ',';

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiter)
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreHeaderCase(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .setAllowMissingColumnNames(true)
                .get();
        try (CSVParser parser = CSVParser.parse(text, format)) {
            List<String> missing = REQUIRED_HEADERS.stream()
                    .filter(h -> !parser.getHeaderMap().containsKey(h))
                    .toList();
            if (!missing.isEmpty()) {
                throw new BadRequestException("MISSING_COLUMNS", "В файле нет обязательных колонок: "
                        + String.join(", ", missing) + ". Ожидаемый заголовок: " + String.join(";", HEADERS));
            }
            List<CSVRecord> records = parser.getRecords();
            if (records.isEmpty()) {
                throw new BadRequestException("NO_ROWS", "В файле нет строк с данными");
            }
            int max = properties.bookings().importMaxRows();
            if (records.size() > max) {
                throw new BadRequestException("TOO_MANY_ROWS", "В файле больше " + max + " строк");
            }
            return records;
        } catch (IOException | UncheckedIOException | IllegalArgumentException | IllegalStateException e) {
            throw new BadRequestException("BAD_CSV", "Ошибка формата CSV: проверьте разделители и кавычки");
        }
    }

    private static String value(CSVRecord record, String column) {
        return record.isMapped(column) && record.isSet(column) ? record.get(column) : null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String describe(ImportReport.RowAction action, boolean dryRun) {
        if (action == ImportReport.RowAction.CREATE) {
            return dryRun ? "Будет создана" : "Создана";
        }
        return dryRun ? "Будет обновлена" : "Обновлена";
    }

    // ошибка разбора конкретной строки файла
    static class RowException extends RuntimeException {
        RowException(String message) {
            super(message);
        }
    }
}
