package carwash.backend.filter;

import carwash.backend.domain.AuditEvent;
import carwash.backend.domain.Client;
import carwash.backend.domain.Role;
import carwash.backend.domain.UserAccount;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

// условия поиска для клиентов, пользователей и аудита
public final class OtherSpecifications {

    private OtherSpecifications() {
    }

    public static Specification<Client> clients(String q) {
        return (root, query, cb) -> {
            if (!LikePatterns.hasText(q)) {
                return cb.conjunction();
            }
            String pattern = LikePatterns.contains(q);
            return cb.or(
                    cb.like(cb.lower(root.get("fullName")), pattern, LikePatterns.ESCAPE),
                    cb.like(root.get("phone"), pattern, LikePatterns.ESCAPE),
                    cb.like(cb.lower(root.get("email")), pattern, LikePatterns.ESCAPE));
        };
    }

    public static Specification<UserAccount> users(String q, Boolean active, Role role) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (LikePatterns.hasText(q)) {
                String pattern = LikePatterns.contains(q);
                predicates.add(cb.or(
                        cb.like(root.get("email"), pattern, LikePatterns.ESCAPE),
                        cb.like(cb.lower(root.get("fullName")), pattern, LikePatterns.ESCAPE)));
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            if (role != null) {
                Expression<Collection<Role>> roles = root.get("roles");
                predicates.add(cb.isMember(role, roles));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    public static Specification<AuditEvent> audit(AuditFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (LikePatterns.hasText(filter.q())) {
                String pattern = LikePatterns.contains(filter.q());
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("description")), pattern, LikePatterns.ESCAPE),
                        cb.like(cb.lower(root.get("userEmail")), pattern, LikePatterns.ESCAPE),
                        cb.like(cb.lower(root.get("correlationId")), pattern, LikePatterns.ESCAPE)));
            }
            if (filter.eventType() != null) {
                predicates.add(cb.equal(root.get("eventType"), filter.eventType()));
            }
            if (LikePatterns.hasText(filter.entityType())) {
                predicates.add(cb.equal(root.get("entityType"), filter.entityType().trim().toUpperCase(Locale.ROOT)));
            }
            if (filter.entityId() != null) {
                predicates.add(cb.equal(root.get("entityId"), filter.entityId()));
            }
            if (LikePatterns.hasText(filter.userEmail())) {
                predicates.add(cb.equal(root.get("userEmail"), filter.userEmail().trim().toLowerCase(Locale.ROOT)));
            }
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), filter.to()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
