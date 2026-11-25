package carwash.backend.controller;

import carwash.backend.config.AppProperties;
import carwash.backend.domain.Role;
import carwash.backend.dto.AdminUserResponse;
import carwash.backend.dto.CreateUserRequest;
import carwash.backend.dto.PageResponse;
import carwash.backend.dto.TemporaryPasswordResponse;
import carwash.backend.dto.UpdateUserRequest;
import carwash.backend.filter.PageRequestFactory;
import carwash.backend.filter.SortFields;
import carwash.backend.security.AuthenticatedUser;
import carwash.backend.service.UserAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Администрирование пользователей")
public class AdminUserController {

    private final UserAdminService userService;
    private final AppProperties properties;

    public AdminUserController(UserAdminService userService, AppProperties properties) {
        this.userService = userService;
        this.properties = properties;
    }

    @GetMapping
    @Operation(summary = "Пользователи с поиском, фильтрами и пагинацией")
    public PageResponse<AdminUserResponse> list(@RequestParam(required = false) String q,
                                                @RequestParam(required = false) Boolean active,
                                                @RequestParam(required = false) Role role,
                                                @RequestParam(required = false) Integer page,
                                                @RequestParam(required = false) Integer size,
                                                @RequestParam(required = false) String sort) {
        return userService.search(q, active, role, PageRequestFactory.create(page, size, sort,
                SortFields.USERS, SortFields.USERS_DEFAULT, properties.bookings().maxPageSize()));
    }

    @GetMapping("/{id}")
    public AdminUserResponse get(@PathVariable UUID id) {
        return userService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Создание пользователя; в ответе — временный пароль (показывается один раз)")
    public TemporaryPasswordResponse create(@Valid @RequestBody CreateUserRequest request) {
        return userService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Изменение имени, email, ролей и активности")
    public AdminUserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request,
                                    @AuthenticationPrincipal AuthenticatedUser admin) {
        return userService.update(id, request, admin.id());
    }

    @PostMapping("/{id}/reset-password")
    @Operation(summary = "Сброс пароля: новый временный пароль, разблокировка, отзыв сессий")
    public TemporaryPasswordResponse resetPassword(@PathVariable UUID id) {
        return userService.resetPassword(id);
    }
}
