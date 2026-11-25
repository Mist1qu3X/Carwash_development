package carwash.backend.service;

import carwash.backend.domain.AuditEventType;
import carwash.backend.domain.ServiceType;
import carwash.backend.dto.ServiceTypeRequest;
import carwash.backend.dto.ServiceTypeResponse;
import carwash.backend.error.BadRequestException;
import carwash.backend.error.ConflictException;
import carwash.backend.error.NotFoundException;
import carwash.backend.repository.ServiceTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

// справочник видов услуг: без физического удаления, только включение/отключение
@Service
public class ServiceTypeService {

    private final ServiceTypeRepository repository;
    private final AuditService audit;

    public ServiceTypeService(ServiceTypeRepository repository, AuditService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<ServiceTypeResponse> list(boolean includeInactive) {
        List<ServiceType> list = includeInactive
                ? repository.findAllByOrderByNameAsc()
                : repository.findAllByActiveTrueOrderByNameAsc();
        return list.stream().map(ServiceTypeResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ServiceTypeResponse get(UUID id) {
        return ServiceTypeResponse.from(load(id));
    }

    @Transactional
    public ServiceTypeResponse create(ServiceTypeRequest request) {
        String code = request.code().trim();
        String name = request.name().trim();
        if (repository.existsByCode(code)) {
            throw new ConflictException("DUPLICATE_CODE", "Услуга с кодом " + code + " уже существует");
        }
        if (repository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("DUPLICATE_NAME", "Услуга с названием «" + name + "» уже существует");
        }
        ServiceType type = new ServiceType();
        type.setCode(code);
        apply(type, request);
        type.setActive(true);
        repository.save(type);
        audit.log(AuditEventType.SERVICE_TYPE_CREATED, EntityTypes.SERVICE_TYPE, type.getId(),
                "Создана услуга " + code + " «" + name + "»");
        return ServiceTypeResponse.from(type);
    }

    @Transactional
    public ServiceTypeResponse update(UUID id, ServiceTypeRequest request) {
        if (request.version() == null) {
            throw new BadRequestException("VERSION_REQUIRED", "Для изменения услуги нужно передать version");
        }
        ServiceType type = load(id);
        if (!Objects.equals(type.getVersion(), request.version())) {
            throw ConflictException.version(type.getVersion());
        }
        // код — внешний идентификатор (используется в CSV), поэтому его не меняем
        if (!type.getCode().equals(request.code().trim())) {
            throw new BadRequestException("CODE_IMMUTABLE", "Код услуги изменить нельзя");
        }
        String name = request.name().trim();
        repository.findByNameIgnoreCase(name).ifPresent(other -> {
            if (!other.getId().equals(id)) {
                throw new ConflictException("DUPLICATE_NAME", "Услуга с названием «" + name + "» уже существует");
            }
        });
        apply(type, request);
        repository.flush();
        audit.log(AuditEventType.SERVICE_TYPE_UPDATED, EntityTypes.SERVICE_TYPE, type.getId(),
                "Изменена услуга " + type.getCode());
        return ServiceTypeResponse.from(type);
    }

    @Transactional
    public ServiceTypeResponse setActive(UUID id, boolean active) {
        ServiceType type = load(id);
        if (type.isActive() != active) {
            type.setActive(active);
            repository.flush();
            audit.log(active ? AuditEventType.SERVICE_TYPE_ENABLED : AuditEventType.SERVICE_TYPE_DISABLED,
                    EntityTypes.SERVICE_TYPE, type.getId(),
                    (active ? "Включена услуга " : "Отключена услуга ") + type.getCode());
        }
        return ServiceTypeResponse.from(type);
    }

    private ServiceType load(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Услуга не найдена"));
    }

    private static void apply(ServiceType type, ServiceTypeRequest request) {
        type.setName(request.name().trim());
        String description = request.description();
        type.setDescription(description == null || description.isBlank() ? null : description.trim());
        type.setBasePrice(request.basePrice().setScale(2, RoundingMode.HALF_UP));
        type.setDurationMinutes(request.durationMinutes());
    }
}
