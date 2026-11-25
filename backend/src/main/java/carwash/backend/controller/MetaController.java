package carwash.backend.controller;

import carwash.backend.config.AppProperties;
import carwash.backend.domain.AuditEventType;
import carwash.backend.domain.BookingStatus;
import carwash.backend.domain.PaymentRisk;
import carwash.backend.domain.Role;
import carwash.backend.dto.MetaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

// значения enum с подписями — чтобы web и Android не дублировали их у себя
@RestController
@RequestMapping("/api/meta")
@Tag(name = "Справочные значения")
public class MetaController {

    private final AppProperties properties;

    public MetaController(AppProperties properties) {
        this.properties = properties;
    }

    @GetMapping
    @Operation(summary = "Статусы (с допустимыми переходами), уровни риска, роли, типы событий аудита")
    public MetaResponse meta() {
        List<MetaResponse.StatusItem> statuses = Arrays.stream(BookingStatus.values())
                .map(s -> new MetaResponse.StatusItem(s.name(), s.getTitle(), s.isFinal(),
                        Arrays.stream(BookingStatus.values()).filter(t -> t != s && s.canChangeTo(t)).map(Enum::name).toList()))
                .toList();
        return new MetaResponse(
                statuses,
                Arrays.stream(PaymentRisk.values()).map(r -> new MetaResponse.EnumItem(r.name(), r.getTitle())).toList(),
                Arrays.stream(Role.values()).map(r -> new MetaResponse.EnumItem(r.name(), r.getTitle())).toList(),
                Arrays.stream(AuditEventType.values()).map(t -> new MetaResponse.EnumItem(t.name(), t.getTitle())).toList(),
                properties.bookings().permanentDeleteEnabled());
    }
}
