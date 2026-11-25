package carwash.backend.domain;

import java.math.BigDecimal;

/**
 * Вычисляемый показатель записи — риск неоплаты.
 * Хранится в колонке payment_risk, чтобы по нему можно было фильтровать в SQL,
 * и пересчитывается при каждом изменении статуса, оплаты или цены.
 */
public enum PaymentRisk {

    LOW("Низкий"),
    MEDIUM("Средний"),
    HIGH("Высокий");

    public static final BigDecimal HIGH_PRICE = new BigDecimal("3000");
    public static final BigDecimal MEDIUM_PRICE = new BigDecimal("1000");

    private final String title;

    PaymentRisk(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    /**
     * Правила (те же, что в миграции V3):
     * 1) отменена, уже оплачена или бесплатна — риска нет (LOW);
     * 2) услуга оказана, а деньги не получены — HIGH;
     * 3) дорогая неоплаченная услуга (от 3000 руб.) — HIGH;
     * 4) средняя сумма (от 1000 руб.) или машина уже в работе — MEDIUM;
     * 5) остальное — LOW.
     */
    public static PaymentRisk calculate(BookingStatus status, boolean paid, BigDecimal price) {
        if (status == BookingStatus.CANCELLED || paid || price == null || price.signum() == 0) {
            return LOW;
        }
        if (status == BookingStatus.COMPLETED) {
            return HIGH;
        }
        if (price.compareTo(HIGH_PRICE) >= 0) {
            return HIGH;
        }
        if (price.compareTo(MEDIUM_PRICE) >= 0 || status == BookingStatus.IN_PROGRESS) {
            return MEDIUM;
        }
        return LOW;
    }
}
