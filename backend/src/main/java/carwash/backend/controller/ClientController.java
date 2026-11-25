package carwash.backend.controller;

import carwash.backend.config.AppProperties;
import carwash.backend.dto.ClientRequest;
import carwash.backend.dto.ClientResponse;
import carwash.backend.dto.PageResponse;
import carwash.backend.filter.PageRequestFactory;
import carwash.backend.filter.SortFields;
import carwash.backend.service.ClientService;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/clients")
@Tag(name = "Клиенты")
public class ClientController {

    private final ClientService clientService;
    private final AppProperties properties;

    public ClientController(ClientService clientService, AppProperties properties) {
        this.clientService = clientService;
        this.properties = properties;
    }

    @GetMapping
    @PreAuthorize("hasRole('VIEWER')")
    @Operation(summary = "Клиенты: поиск по имени, телефону, email")
    public PageResponse<ClientResponse> list(@RequestParam(required = false) String q,
                                             @RequestParam(required = false) Integer page,
                                             @RequestParam(required = false) Integer size,
                                             @RequestParam(required = false) String sort) {
        return clientService.search(q, PageRequestFactory.create(page, size, sort,
                SortFields.CLIENTS, SortFields.CLIENTS_DEFAULT, properties.bookings().maxPageSize()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('VIEWER')")
    public ClientResponse get(@PathVariable UUID id) {
        return clientService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MANAGER')")
    public ClientResponse create(@Valid @RequestBody ClientRequest request) {
        return clientService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ClientResponse update(@PathVariable UUID id, @Valid @RequestBody ClientRequest request) {
        return clientService.update(id, request);
    }
}
