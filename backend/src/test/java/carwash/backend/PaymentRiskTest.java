package carwash.backend;

import carwash.backend.domain.Booking;
import carwash.backend.domain.BookingStatus;
import carwash.backend.domain.PaymentRisk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

// тест бизнес-правила: вычисляемый показатель "риск неоплаты" и переходы статусов
class PaymentRiskTest {

    @ParameterizedTest(name = "{0}, оплачено={1}, цена={2} -> {3}")
    @CsvSource({
            "CREATED,     false, 500,  LOW",
            "CREATED,     false, 1200, MEDIUM",
            "CONFIRMED,   false, 3000, HIGH",
            "IN_PROGRESS, false, 500,  MEDIUM",
            "COMPLETED,   false, 500,  HIGH",
            "COMPLETED,   true,  5000, LOW",
            "CANCELLED,   false, 5000, LOW",
            "CREATED,     false, 0,    LOW",
            "CONFIRMED,   true,  9000, LOW"
    })
    void calculatesRisk(BookingStatus status, boolean paid, BigDecimal price, PaymentRisk expected) {
        assertThat(PaymentRisk.calculate(status, paid, price)).isEqualTo(expected);
    }

    @Test
    void riskIsRecalculatedWhenBookingChanges() {
        Booking booking = new Booking();
        booking.setPrice(new BigDecimal("5000"));
        assertThat(booking.getPaymentRisk()).isEqualTo(PaymentRisk.HIGH);

        booking.setPaid(true);
        assertThat(booking.getPaymentRisk()).isEqualTo(PaymentRisk.LOW);

        booking.setPaid(false);
        booking.setStatus(BookingStatus.CANCELLED);
        assertThat(booking.getPaymentRisk()).isEqualTo(PaymentRisk.LOW);
    }

    @Test
    void statusTransitionsFollowKr1Rules() {
        assertThat(BookingStatus.CREATED.canChangeTo(BookingStatus.CONFIRMED)).isTrue();
        assertThat(BookingStatus.CREATED.canChangeTo(BookingStatus.COMPLETED)).isFalse();
        assertThat(BookingStatus.IN_PROGRESS.canChangeTo(BookingStatus.COMPLETED)).isTrue();
        assertThat(BookingStatus.COMPLETED.canChangeTo(BookingStatus.CANCELLED)).isFalse();
        assertThat(BookingStatus.CANCELLED.canChangeTo(BookingStatus.CANCELLED)).isTrue();
    }
}
