package carwash.backend.controller;

import carwash.backend.dto.AuditEventResponse;
import carwash.backend.dto.PageResponse;
import carwash.backend.filter.AuditFilter;
import carwash.backend.filter.PageRequestFactory;
import carwash.backend.filter.SortFields;
import carwash.backend.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/audit")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Журнал аудита")
public class AuditController {

    private static final int MAX_PAGE_SIZE = 200;

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @Operation(summary = "Журнал аудита: поиск, фильтры, сортировка (createdAt, eventType, userEmail, entityType)")
    public PageResponse<AuditEventResponse> list(@ParameterObject AuditFilter filter,
                                                 @RequestParam(required = false) Integer page,
                                                 @RequestParam(required = false) Integer size,
                                                 @RequestParam(required = false) String sort) {
        return auditService.search(filter, PageRequestFactory.create(page, size, sort,
                SortFields.AUDIT, SortFields.AUDIT_DEFAULT, MAX_PAGE_SIZE));
    }
}
