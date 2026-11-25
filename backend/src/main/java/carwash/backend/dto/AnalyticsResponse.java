package carwash.backend.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Сводка аналитической панели.
 * total/active/archived — по всем записям; остальные показатели — по активным (не архивным).
 */
public record AnalyticsResponse(
        long total,
        long active,
        long archived,
        long paid,
        long unpaid,
        BigDecimal averagePrice,
        BigDecimal paidRevenue,
        BigDecimal unpaidDebt,
        List<CountItem> byServiceType,
        List<CountItem> byStatus,
        List<CountItem> byPaymentRisk) {

    public record CountItem(String key, String title, long count) {
    }
}
