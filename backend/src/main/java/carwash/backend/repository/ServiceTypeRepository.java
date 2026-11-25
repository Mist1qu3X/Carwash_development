package carwash.backend.repository;

import carwash.backend.domain.ServiceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceTypeRepository extends JpaRepository<ServiceType, UUID> {

    List<ServiceType> findAllByOrderByNameAsc();

    List<ServiceType> findAllByActiveTrueOrderByNameAsc();

    Optional<ServiceType> findByCode(String code);

    Optional<ServiceType> findByNameIgnoreCase(String name);

    boolean existsByCode(String code);

    boolean existsByNameIgnoreCase(String name);
}
