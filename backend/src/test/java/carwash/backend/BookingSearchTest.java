package carwash.backend;

import carwash.backend.domain.Booking;
import carwash.backend.domain.BookingStatus;
import carwash.backend.domain.Client;
import carwash.backend.domain.PaymentRisk;
import carwash.backend.domain.ServiceType;
import carwash.backend.error.BadRequestException;
import carwash.backend.filter.ArchiveScope;
import carwash.backend.filter.BookingFilter;
import carwash.backend.filter.BookingSpecifications;
import carwash.backend.filter.NumberOperation;
import carwash.backend.filter.PageRequestFactory;
import carwash.backend.filter.SortFields;
import carwash.backend.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// тест поиска и фильтрации (Specification -> SQL на H2), пагинации и сортировки
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class BookingSearchTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private BookingRepository bookings;

    private UUID polishId;

    @BeforeEach
    void setUp() {
        ServiceType wash = serviceType("BODY_WASH", "Мойка кузова");
        ServiceType polish = serviceType("POLISHING", "Полировка кузова");
        polishId = polish.getId();
        Client ivan = client("Иван Петров", "+7-900-111-11-11");
        Client maria = client("Мария Сидорова", "+7-900-222-22-22");

        booking("CW-000001", ivan, "А123ВС77", "Toyota Camry", wash, BookingStatus.CREATED, "500", false, Set.of("постоянный"), false);
        booking("CW-000002", ivan, "А123ВС77", "Toyota Camry", polish, BookingStatus.COMPLETED, "5000", true, Set.of("vip"), false);
        booking("CW-000003", maria, "В456ОР99", "Kia Rio", polish, BookingStatus.CONFIRMED, "5500", false, Set.of(), false);
        booking("CW-000004", maria, "В456ОР99", "Kia Rio", wash, BookingStatus.CANCELLED, "500", false, Set.of("vip"), true);
        em.flush();
        em.clear();
    }

    @Test
    void byDefaultOnlyActiveBookingsAreReturned() {
        assertThat(codes(new Filter())).containsExactly("CW-000001", "CW-000002", "CW-000003");
        assertThat(codes(new Filter().archive(ArchiveScope.ARCHIVED))).containsExactly("CW-000004");
        assertThat(codes(new Filter().archive(ArchiveScope.ALL))).hasSize(4);
    }

    @Test
    void freeTextSearchesCodeCarClientAndPhone() {
        assertThat(codes(new Filter().q("toyota"))).containsExactly("CW-000001", "CW-000002");
        assertThat(codes(new Filter().q("мария"))).containsExactly("CW-000003");
        assertThat(codes(new Filter().q("в456"))).containsExactly("CW-000003");
        assertThat(codes(new Filter().q("222-22"))).containsExactly("CW-000003");
        assertThat(codes(new Filter().q("cw-000002"))).containsExactly("CW-000002");
    }

    @Test
    void likeWildcardsFromUserAreEscaped() {
        assertThat(codes(new Filter().q("%"))).isEmpty();
        assertThat(codes(new Filter().q("_"))).isEmpty();
        assertThat(codes(new Filter().q("' or 1=1 --"))).isEmpty();
    }

    @Test
    void filtersByStatusPaidRiskTagAndServiceType() {
        assertThat(codes(new Filter().status(BookingStatus.CONFIRMED))).containsExactly("CW-000003");
        assertThat(codes(new Filter().paid(true))).containsExactly("CW-000002");
        assertThat(codes(new Filter().risk(PaymentRisk.HIGH))).containsExactly("CW-000003");
        assertThat(codes(new Filter().tag("VIP"))).containsExactly("CW-000002");
        assertThat(codes(new Filter().serviceType(polishId))).containsExactly("CW-000002", "CW-000003");
    }

    @Test
    void filtersByPriceWithComparison() {
        assertThat(codes(new Filter().price(NumberOperation.GT, "1000"))).containsExactly("CW-000002", "CW-000003");
        assertThat(codes(new Filter().price(NumberOperation.LT, "1000"))).containsExactly("CW-000001");
        assertThat(codes(new Filter().price(NumberOperation.EQ, "5500"))).containsExactly("CW-000003");
        assertThat(codes(new Filter().price(NumberOperation.LTE, "5000"))).containsExactly("CW-000001", "CW-000002");
        assertThat(codes(new Filter().q("kia").price(NumberOperation.GTE, "5000"))).containsExactly("CW-000003");
    }

    @Test
    void paginationAndSorting() {
        Pageable pageable = PageRequestFactory.create(0, 2, "price,desc", SortFields.BOOKINGS, SortFields.BOOKINGS_DEFAULT, 100);
        Page<Booking> page = bookings.findAll(BookingSpecifications.fromFilter(new Filter().build()), pageable);
        assertThat(page.getContent()).extracting(Booking::getCode).containsExactly("CW-000003", "CW-000002");
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);

        Pageable byClient = PageRequestFactory.create(0, 10, "clientName,desc;code,asc", SortFields.BOOKINGS, SortFields.BOOKINGS_DEFAULT, 100);
        assertThat(bookings.findAll(BookingSpecifications.fromFilter(new Filter().build()), byClient).getContent())
                .extracting(Booking::getCode).containsExactly("CW-000003", "CW-000001", "CW-000002");
    }

    @Test
    void pageSizeIsLimitedAndUnknownSortFieldIsRejected() {
        assertThat(PageRequestFactory.create(0, 100_000, null, SortFields.BOOKINGS, SortFields.BOOKINGS_DEFAULT, 100).getPageSize())
                .isEqualTo(100);
        assertThatThrownBy(() -> PageRequestFactory.create(0, 10, "password,asc", SortFields.BOOKINGS, SortFields.BOOKINGS_DEFAULT, 100))
                .isInstanceOf(BadRequestException.class);
    }

    // ---------- вспомогательные методы ----------

    private List<String> codes(Filter filter) {
        return bookings.findAll(BookingSpecifications.fromFilter(filter.build()), PageRequest.of(0, 50, Sort.by("code")))
                .map(Booking::getCode).getContent();
    }

    private ServiceType serviceType(String code, String name) {
        ServiceType type = new ServiceType();
        type.setCode(code);
        type.setName(name);
        type.setBasePrice(new BigDecimal("500.00"));
        type.setDurationMinutes(30);
        return em.persist(type);
    }

    private Client client(String name, String phone) {
        Client client = new Client();
        client.setFullName(name);
        client.setPhone(phone);
        return em.persist(client);
    }

    private void booking(String code, Client client, String carNumber, String model, ServiceType type,
                         BookingStatus status, String price, boolean paid, Set<String> tags, boolean archived) {
        Booking b = new Booking();
        b.setCode(code);
        b.setClient(client);
        b.setCarNumber(carNumber);
        b.setCarModel(model);
        b.setServiceType(type);
        b.setStatus(status);
        b.setScheduledAt(LocalDateTime.of(2026, 12, 1, 10, 0));
        b.setPrice(new BigDecimal(price));
        b.setPaid(paid);
        b.replaceTags(tags);
        if (archived) {
            b.archive(Instant.now());
        }
        em.persist(b);
    }

    // маленький builder, чтобы не перечислять все 12 параметров BookingFilter в каждом тесте
    private static class Filter {
        private String q;
        private UUID serviceTypeId;
        private BookingStatus status;
        private Boolean paid;
        private PaymentRisk risk;
        private String tag;
        private ArchiveScope archive;
        private BigDecimal price;
        private NumberOperation op;

        Filter q(String v) { q = v; return this; }
        Filter serviceType(UUID v) { serviceTypeId = v; return this; }
        Filter status(BookingStatus v) { status = v; return this; }
        Filter paid(Boolean v) { paid = v; return this; }
        Filter risk(PaymentRisk v) { risk = v; return this; }
        Filter tag(String v) { tag = v; return this; }
        Filter archive(ArchiveScope v) { archive = v; return this; }
        Filter price(NumberOperation o, String v) { op = o; price = new BigDecimal(v); return this; }

        BookingFilter build() {
            return new BookingFilter(q, serviceTypeId, null, status, paid, risk, tag, archive, price, op, null, null);
        }
    }
}
