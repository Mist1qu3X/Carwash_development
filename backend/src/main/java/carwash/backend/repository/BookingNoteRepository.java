package carwash.backend.repository;

import carwash.backend.domain.BookingNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingNoteRepository extends JpaRepository<BookingNote, UUID> {

    // закреплённые заметки сверху, затем новые
    List<BookingNote> findByBookingIdOrderByPinnedDescCreatedAtDesc(UUID bookingId);

    Optional<BookingNote> findByIdAndBookingId(UUID id, UUID bookingId);
}
