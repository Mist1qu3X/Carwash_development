package carwash.backend.controller;

import carwash.backend.dto.ServiceTypeRequest;
import carwash.backend.dto.ServiceTypeResponse;
import carwash.backend.service.ServiceTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// справочник видов услуг (в задании условно /api/references); изменение — только ADMIN
@RestController
@RequestMapping("/api/service-types")
@Tag(name = "Справочник услуг")
public class ServiceTypeController {

    private final ServiceTypeService service;

    public ServiceTypeController(ServiceTypeService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('VIEWER')")
    @Operation(summary = "Список услуг (по умолчанию вместе с отключёнными)")
    public List<ServiceTypeResponse> list(@RequestParam(defaultValue = "true") boolean includeInactive) {
        return service.list(includeInactive);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('VIEWER')")
    public ServiceTypeResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Новая услуга")
    public ServiceTypeResponse create(@Valid @RequestBody ServiceTypeRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Изменение услуги (код не меняется)")
    public ServiceTypeResponse update(@PathVariable UUID id, @Valid @RequestBody ServiceTypeRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/enable")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Включить услугу")
    public ServiceTypeResponse enable(@PathVariable UUID id) {
        return service.setActive(id, true);
    }

    @PostMapping("/{id}/disable")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Отключить услугу (без удаления)")
    public ServiceTypeResponse disable(@PathVariable UUID id) {
        return service.setActive(id, false);
    }
}
