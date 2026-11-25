package carwash.backend.filter;

import carwash.backend.domain.Booking;
import carwash.backend.domain.Client;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Построение условий поиска через JPA Criteria API.
 * Значения из запроса попадают в SQL только как параметры — конкатенации строк нет.
 */
public final class BookingSpecifications {

    private BookingSpecifications() {
    }

    public static Specification<Booking> fromFilter(BookingFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            switch (filter.archiveOrDefault()) {
                case ACTIVE -> predicates.add(cb.isFalse(root.get("archived")));
                case ARCHIVED -> predicates.add(cb.isTrue(root.get("archived")));
                case ALL -> { /* без условия */ }
            }

            if (LikePatterns.hasText(filter.q())) {
                String pattern = LikePatterns.contains(filter.q());
                Join<Booking, Client> client = root.join("client", JoinType.INNER);
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("code")), pattern, LikePatterns.ESCAPE),
                        cb.like(cb.lower(root.get("carNumber")), pattern, LikePatterns.ESCAPE),
                        cb.like(cb.lower(root.get("carModel")), pattern, LikePatterns.ESCAPE),
                        cb.like(cb.lower(client.get("fullName")), pattern, LikePatterns.ESCAPE),
                        cb.like(client.get("phone"), pattern, LikePatterns.ESCAPE)));
            }
            if (filter.serviceTypeId() != null) {
                predicates.add(cb.equal(root.get("serviceType").get("id"), filter.serviceTypeId()));
            }
            if (filter.clientId() != null) {
                predicates.add(cb.equal(root.get("client").get("id"), filter.clientId()));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.paid() != null) {
                predicates.add(cb.equal(root.get("paid"), filter.paid()));
            }
            if (filter.risk() != null) {
                predicates.add(cb.equal(root.get("paymentRisk"), filter.risk()));
            }
            if (LikePatterns.hasText(filter.tag())) {
                Expression<java.util.Collection<String>> tags = root.get("tags");
                predicates.add(cb.isMember(filter.tag().trim().toLowerCase(Locale.ROOT), tags));
            }
            if (filter.price() != null) {
                Path<BigDecimal> price = root.get("price");
                BigDecimal value = filter.price();
                NumberOperation op = filter.priceOp() == null ? NumberOperation.GTE : filter.priceOp();
                predicates.add(switch (op) {
                    case EQ -> cb.equal(price, value);
                    case GT -> cb.greaterThan(price, value);
                    case GTE -> cb.greaterThanOrEqualTo(price, value);
                    case LT -> cb.lessThan(price, value);
                    case LTE -> cb.lessThanOrEqualTo(price, value);
                });
            }
            if (filter.scheduledFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("scheduledAt"), filter.scheduledFrom()));
            }
            if (filter.scheduledTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("scheduledAt"), filter.scheduledTo()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
