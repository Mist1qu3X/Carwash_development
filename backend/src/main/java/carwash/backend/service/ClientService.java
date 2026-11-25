package carwash.backend.service;

import carwash.backend.domain.AuditEventType;
import carwash.backend.domain.Client;
import carwash.backend.dto.ClientRequest;
import carwash.backend.dto.ClientResponse;
import carwash.backend.dto.PageResponse;
import carwash.backend.error.BadRequestException;
import carwash.backend.error.ConflictException;
import carwash.backend.error.NotFoundException;
import carwash.backend.filter.OtherSpecifications;
import carwash.backend.repository.ClientRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

// клиенты автомойки (логика из ClientService КР1 + версия и аудит)
@Service
public class ClientService {

    private final ClientRepository clients;
    private final AuditService audit;

    public ClientService(ClientRepository clients, AuditService audit) {
        this.clients = clients;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<ClientResponse> search(String q, Pageable pageable) {
        return PageResponse.of(clients.findAll(OtherSpecifications.clients(q), pageable), ClientResponse::from);
    }

    @Transactional(readOnly = true)
    public ClientResponse get(UUID id) {
        return ClientResponse.from(load(id));
    }

    @Transactional
    public ClientResponse create(ClientRequest request) {
        String phone = request.phone().trim();
        if (clients.findByPhone(phone).isPresent()) {
            throw new ConflictException("DUPLICATE_PHONE", "Клиент с телефоном " + phone + " уже существует");
        }
        Client client = new Client();
        apply(client, request);
        clients.save(client);
        audit.log(AuditEventType.CLIENT_CREATED, EntityTypes.CLIENT, client.getId(),
                "Создан клиент " + client.getFullName() + " (" + phone + ")");
        return ClientResponse.from(client);
    }

    @Transactional
    public ClientResponse update(UUID id, ClientRequest request) {
        if (request.version() == null) {
            throw new BadRequestException("VERSION_REQUIRED", "Для изменения клиента нужно передать version");
        }
        Client client = load(id);
        if (!Objects.equals(client.getVersion(), request.version())) {
            throw ConflictException.version(client.getVersion());
        }
        String phone = request.phone().trim();
        clients.findByPhone(phone).ifPresent(other -> {
            if (!other.getId().equals(id)) {
                throw new ConflictException("DUPLICATE_PHONE", "Телефон " + phone + " занят другим клиентом");
            }
        });
        apply(client, request);
        clients.flush();
        audit.log(AuditEventType.CLIENT_UPDATED, EntityTypes.CLIENT, client.getId(),
                "Изменён клиент " + client.getFullName());
        return ClientResponse.from(client);
    }

    public Client load(UUID id) {
        return clients.findById(id).orElseThrow(() -> new NotFoundException("Клиент не найден"));
    }

    private static void apply(Client client, ClientRequest request) {
        client.setFullName(request.fullName().trim());
        client.setPhone(request.phone().trim());
        String email = request.email();
        client.setEmail(email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT));
    }
}
