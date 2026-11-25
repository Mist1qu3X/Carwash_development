package carwash.backend.dto;

import carwash.backend.domain.Client;

import java.time.Instant;
import java.util.UUID;

public record ClientResponse(UUID id, String fullName, String phone, String email,
                             Instant createdAt, Instant updatedAt, Long version) {

    public static ClientResponse from(Client c) {
        return new ClientResponse(c.getId(), c.getFullName(), c.getPhone(), c.getEmail(),
                c.getCreatedAt(), c.getUpdatedAt(), c.getVersion());
    }
}
