package carwash.backend.repository;

import carwash.backend.domain.Role;
import carwash.backend.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID>, JpaSpecificationExecutor<UserAccount> {

    Optional<UserAccount> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("select count(distinct u) from UserAccount u join u.roles r where r = :role and u.active = true")
    long countActiveWithRole(Role role);
}
