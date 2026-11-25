package carwash.backend.service;

import carwash.backend.domain.BookingStatus;
import carwash.backend.domain.PaymentRisk;
import carwash.backend.dto.AnalyticsResponse;
import carwash.backend.repository.BookingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// сводные показатели: считаются агрегатными запросами в PostgreSQL, а не в Java
@Service
public class AnalyticsService {

    private final BookingRepository bookings;

    public AnalyticsService(BookingRepository bookings) {
        this.bookings = bookings;
    }

    @Transactional(readOnly = true)
    public AnalyticsResponse summary() {
        long active = bookings.countByArchived(false);
        long archived = bookings.countByArchived(true);
        Double average = bookings.averageActivePrice();

        List<AnalyticsResponse.CountItem> byServiceType = bookings.countActiveByServiceType().stream()
                .map(row -> new AnalyticsResponse.CountItem((String) row[0], (String) row[1], ((Number) row[2]).longValue()))
                .toList();

        Map<BookingStatus, Long> statusCounts = toMap(bookings.countActiveByStatus(), BookingStatus.class);
        List<AnalyticsResponse.CountItem> byStatus = Arrays.stream(BookingStatus.values())
                .map(s -> new AnalyticsResponse.CountItem(s.name(), s.getTitle(), statusCounts.getOrDefault(s, 0L)))
                .toList();

        Map<PaymentRisk, Long> riskCounts = toMap(bookings.countActiveByPaymentRisk(), PaymentRisk.class);
        List<AnalyticsResponse.CountItem> byRisk = Arrays.stream(PaymentRisk.values())
                .map(r -> new AnalyticsResponse.CountItem(r.name(), r.getTitle(), riskCounts.getOrDefault(r, 0L)))
                .toList();

        return new AnalyticsResponse(
                active + archived,
                active,
                archived,
                bookings.countByArchivedFalseAndPaid(true),
                bookings.countByArchivedFalseAndPaid(false),
                average == null ? BigDecimal.ZERO.setScale(2) : BigDecimal.valueOf(average).setScale(2, RoundingMode.HALF_UP),
                bookings.sumPaidRevenue().setScale(2, RoundingMode.HALF_UP),
                bookings.sumUnpaidDebt().setScale(2, RoundingMode.HALF_UP),
                byServiceType,
                byStatus,
                byRisk);
    }

    private static <E extends Enum<E>> Map<E, Long> toMap(List<Object[]> rows, Class<E> type) {
        return rows.stream().collect(Collectors.toMap(
                row -> type.cast(row[0]),
                row -> ((Number) row[1]).longValue()));
    }
}
