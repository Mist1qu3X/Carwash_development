package carwash.backend.repository;

import carwash.backend.domain.Booking;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID>, JpaSpecificationExecutor<Booking> {

    // клиент и услуга подгружаются одним запросом, теги — пачками (@BatchSize)
    @Override
    @EntityGraph(attributePaths = {"client", "serviceType"})
    Page<Booking> findAll(Specification<Booking> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"client", "serviceType"})
    Optional<Booking> findWithDetailsById(UUID id);

    @EntityGraph(attributePaths = {"client", "serviceType"})
    Optional<Booking> findByCode(String code);

    boolean existsByCode(String code);

    @Query(value = "select nextval('booking_code_seq')", nativeQuery = true)
    long nextCodeNumber();

    @Query("select distinct t from Booking b join b.tags t order by t")
    List<String> findAllTags();

    // ---------- аналитика ----------

    long countByArchived(boolean archived);

    long countByArchivedFalseAndPaid(boolean paid);

    @Query("select avg(b.price) from Booking b where b.archived = false")
    Double averageActivePrice();

    // получено денег: оплаченные и не отменённые записи
    @Query("select coalesce(sum(b.price), 0) from Booking b where b.archived = false and b.paid = true "
            + "and b.status <> carwash.backend.domain.BookingStatus.CANCELLED")
    BigDecimal sumPaidRevenue();

    // долг клиентов: услуга оказана (COMPLETED), но не оплачена
    @Query("select coalesce(sum(b.price), 0) from Booking b where b.archived = false and b.paid = false "
            + "and b.status = carwash.backend.domain.BookingStatus.COMPLETED")
    BigDecimal sumUnpaidDebt();

    @Query("select s.code, s.name, count(b) from Booking b join b.serviceType s "
            + "where b.archived = false group by s.code, s.name order by count(b) desc, s.name")
    List<Object[]> countActiveByServiceType();

    @Query("select b.status, count(b) from Booking b where b.archived = false group by b.status")
    List<Object[]> countActiveByStatus();

    @Query("select b.paymentRisk, count(b) from Booking b where b.archived = false group by b.paymentRisk")
    List<Object[]> countActiveByPaymentRisk();
}
