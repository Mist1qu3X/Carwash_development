package carwash.backend.service;

import carwash.backend.config.CorrelationIdFilter;
import carwash.backend.domain.AuditEvent;
import carwash.backend.domain.AuditEventType;
import carwash.backend.dto.AuditEventResponse;
import carwash.backend.dto.PageResponse;
import carwash.backend.filter.AuditFilter;
import carwash.backend.filter.OtherSpecifications;
import carwash.backend.repository.AuditEventRepository;
import carwash.backend.security.AuthenticatedUser;
import carwash.backend.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.util.UUID;

/**
 * Журнал аудита. Событие пишется в той же транзакции, что и изменение данных:
 * если изменение откатилось, то и записи в журнале не будет.
 */
@Service
public class AuditService {

    private static final int MAX_DESCRIPTION = 1000;

    private final AuditEventRepository repository;
    private final Clock clock;

    public AuditService(AuditEventRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    // событие от имени текущего пользователя из SecurityContext
    @Transactional
    public void log(AuditEventType type, String entityType, UUID entityId, String description) {
        AuthenticatedUser user = CurrentUser.find().orElse(null);
        save(type, user == null ? null : user.id(), user == null ? null : user.email(), entityType, entityId, description);
    }

    // событие с явно указанным пользователем (вход, когда SecurityContext ещё пуст)
    @Transactional
    public void logAs(AuditEventType type, UUID userId, String userEmail, String entityType, UUID entityId, String description) {
        save(type, userId, userEmail, entityType, entityId, description);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEventResponse> search(AuditFilter filter, Pageable pageable) {
        return PageResponse.of(repository.findAll(OtherSpecifications.audit(filter), pageable), AuditEventResponse::from);
    }

    private void save(AuditEventType type, UUID userId, String userEmail, String entityType, UUID entityId, String description) {
        AuditEvent event = new AuditEvent();
        event.setCreatedAt(clock.instant());
        event.setUserId(userId);
        event.setUserEmail(userEmail);
        event.setEventType(type);
        event.setEntityType(entityType);
        event.setEntityId(entityId);
        String text = description == null ? type.getTitle() : description;
        event.setDescription(text.length() > MAX_DESCRIPTION ? text.substring(0, MAX_DESCRIPTION) : text);
        event.setCorrelationId(MDC.get(CorrelationIdFilter.MDC_KEY));
        event.setIpAddress(currentIp());
        repository.save(event);
    }

    private static String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return request.getRemoteAddr();
        }
        return null;
    }
}
