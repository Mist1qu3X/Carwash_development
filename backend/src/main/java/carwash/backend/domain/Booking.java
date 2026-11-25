package carwash.backend.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Запись на автомойку — основная сущность системы.
 * Риск неоплаты (paymentRisk) не задаётся снаружи: он пересчитывается
 * автоматически при изменении статуса, оплаты или цены.
 */
@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // внешний код записи (CW-000001), по нему работает импорт CSV
    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "car_number", nullable = false, length = 20)
    private String carNumber;

    @Column(name = "car_model", length = 50)
    private String carModel;

    @Column(name = "customer_comment", length = 500)
    private String customerComment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_type_id", nullable = false)
    private ServiceType serviceType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status = BookingStatus.CREATED;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean paid;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_risk", nullable = false, length = 10)
    private PaymentRisk paymentRisk = PaymentRisk.LOW;

    @ElementCollection
    @CollectionTable(name = "booking_tags", joinColumns = @JoinColumn(name = "booking_id"))
    @Column(name = "tag", nullable = false, length = 30)
    @BatchSize(size = 50)
    private Set<String> tags = new HashSet<>();

    @Column(nullable = false)
    private boolean archived;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // контроль конкурентного изменения: Hibernate увеличивает version при каждом UPDATE
    @Version
    private Long version;

    // ---------- бизнес-методы ----------

    private void recalculatePaymentRisk() {
        if (status != null && price != null) {
            paymentRisk = PaymentRisk.calculate(status, paid, price);
        }
    }

    public void archive(Instant now) {
        this.archived = true;
        this.archivedAt = now;
    }

    public void restore() {
        this.archived = false;
        this.archivedAt = null;
    }

    // меняем коллекцию только если набор тегов действительно другой
    public void replaceTags(Collection<String> newTags) {
        Set<String> target = new HashSet<>(newTags);
        if (!tags.equals(target)) {
            tags.retainAll(target);
            tags.addAll(target);
            touch();
        }
    }

    public boolean addTag(String tag) {
        boolean added = tags.add(tag);
        if (added) {
            touch();
        }
        return added;
    }

    public boolean removeTag(String tag) {
        boolean removed = tags.remove(tag);
        if (removed) {
            touch();
        }
        return removed;
    }

    // изменение только коллекции тегов не меняет колонки bookings, поэтому
    // явно обновляем updated_at, чтобы запись считалась изменённой
    private void touch() {
        this.updatedAt = Instant.now();
    }

    // ---------- геттеры и сеттеры ----------

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Client getClient() {
        return client;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    public String getCarNumber() {
        return carNumber;
    }

    public void setCarNumber(String carNumber) {
        this.carNumber = carNumber;
    }

    public String getCarModel() {
        return carModel;
    }

    public void setCarModel(String carModel) {
        this.carModel = carModel;
    }

    public String getCustomerComment() {
        return customerComment;
    }

    public void setCustomerComment(String customerComment) {
        this.customerComment = customerComment;
    }

    public ServiceType getServiceType() {
        return serviceType;
    }

    public void setServiceType(ServiceType serviceType) {
        this.serviceType = serviceType;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
        recalculatePaymentRisk();
    }

    public LocalDateTime getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(LocalDateTime scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
        recalculatePaymentRisk();
    }

    public boolean isPaid() {
        return paid;
    }

    public void setPaid(boolean paid) {
        this.paid = paid;
        recalculatePaymentRisk();
    }

    public PaymentRisk getPaymentRisk() {
        return paymentRisk;
    }

    public Set<String> getTags() {
        return tags;
    }

    public boolean isArchived() {
        return archived;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
