package carwash.backend.filter;

import org.springframework.data.domain.Sort;

import java.util.Map;

// белые списки полей сортировки: имя в API -> свойство JPA-сущности
public final class SortFields {

    private SortFields() {
    }

    public static final Map<String, String> BOOKINGS = Map.of(
            "scheduledAt", "scheduledAt",
            "price", "price",
            "code", "code",
            "status", "status",
            "carNumber", "carNumber",
            "clientName", "client.fullName",
            "serviceType", "serviceType.name",
            "createdAt", "createdAt",
            "updatedAt", "updatedAt");
    public static final Sort BOOKINGS_DEFAULT = Sort.by(Sort.Direction.DESC, "scheduledAt");

    public static final Map<String, String> CLIENTS = Map.of(
            "fullName", "fullName",
            "phone", "phone",
            "createdAt", "createdAt");
    public static final Sort CLIENTS_DEFAULT = Sort.by("fullName");

    public static final Map<String, String> USERS = Map.of(
            "email", "email",
            "fullName", "fullName",
            "createdAt", "createdAt",
            "lastLoginAt", "lastLoginAt");
    public static final Sort USERS_DEFAULT = Sort.by("email");

    public static final Map<String, String> AUDIT = Map.of(
            "createdAt", "createdAt",
            "eventType", "eventType",
            "userEmail", "userEmail",
            "entityType", "entityType");
    public static final Sort AUDIT_DEFAULT = Sort.by(Sort.Direction.DESC, "createdAt");
}
