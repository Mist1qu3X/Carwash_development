package carwash.backend.dto;

import carwash.backend.domain.BookingNote;

import java.time.Instant;
import java.util.UUID;

public record NoteResponse(UUID id, UUID bookingId, String text, boolean pinned,
                           UUID authorId, String authorName, Instant createdAt, Instant updatedAt, Long version) {

    public static NoteResponse from(BookingNote note) {
        return new NoteResponse(note.getId(), note.getBooking().getId(), note.getText(), note.isPinned(),
                note.getAuthor() == null ? null : note.getAuthor().getId(), note.getAuthorName(),
                note.getCreatedAt(), note.getUpdatedAt(), note.getVersion());
    }
}
