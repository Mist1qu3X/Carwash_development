package carwash.backend.service;

import carwash.backend.domain.AuditEventType;
import carwash.backend.domain.Booking;
import carwash.backend.domain.BookingNote;
import carwash.backend.dto.NoteRequest;
import carwash.backend.dto.NoteResponse;
import carwash.backend.error.ConflictException;
import carwash.backend.error.ForbiddenException;
import carwash.backend.error.NotFoundException;
import carwash.backend.repository.BookingNoteRepository;
import carwash.backend.repository.UserAccountRepository;
import carwash.backend.security.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

// заметки к записи: история, создание, изменение, удаление, закрепление
@Service
public class BookingNoteService {

    private final BookingNoteRepository notes;
    private final BookingService bookingService;
    private final UserAccountRepository users;
    private final AuditService audit;

    public BookingNoteService(BookingNoteRepository notes, BookingService bookingService,
                              UserAccountRepository users, AuditService audit) {
        this.notes = notes;
        this.bookingService = bookingService;
        this.users = users;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<NoteResponse> list(UUID bookingId) {
        bookingService.load(bookingId);
        return notes.findByBookingIdOrderByPinnedDescCreatedAtDesc(bookingId).stream().map(NoteResponse::from).toList();
    }

    @Transactional
    public NoteResponse create(UUID bookingId, NoteRequest request, AuthenticatedUser author) {
        Booking booking = bookingService.load(bookingId);
        BookingNote note = new BookingNote();
        note.setBooking(booking);
        note.setAuthor(users.getReferenceById(author.id()));
        note.setAuthorName(author.fullName());
        note.setText(request.text().trim());
        note.setPinned(request.pinned());
        notes.save(note);
        audit.log(AuditEventType.NOTE_CREATED, EntityTypes.NOTE, note.getId(),
                "Добавлена заметка к записи " + booking.getCode() + (request.pinned() ? " (закреплена)" : ""));
        return NoteResponse.from(note);
    }

    @Transactional
    public NoteResponse update(UUID bookingId, UUID noteId, NoteRequest request, AuthenticatedUser user) {
        BookingNote note = load(bookingId, noteId);
        checkCanModify(note, user);
        if (request.version() != null && !Objects.equals(note.getVersion(), request.version())) {
            throw ConflictException.version(note.getVersion());
        }
        note.setText(request.text().trim());
        note.setPinned(request.pinned());
        notes.flush();
        audit.log(AuditEventType.NOTE_UPDATED, EntityTypes.NOTE, note.getId(),
                "Изменена заметка к записи " + note.getBooking().getCode());
        return NoteResponse.from(note);
    }

    @Transactional
    public void delete(UUID bookingId, UUID noteId, AuthenticatedUser user) {
        BookingNote note = load(bookingId, noteId);
        checkCanModify(note, user);
        String code = note.getBooking().getCode();
        notes.delete(note);
        audit.log(AuditEventType.NOTE_DELETED, EntityTypes.NOTE, noteId, "Удалена заметка к записи " + code);
    }

    private BookingNote load(UUID bookingId, UUID noteId) {
        return notes.findByIdAndBookingId(noteId, bookingId)
                .orElseThrow(() -> new NotFoundException("Заметка не найдена"));
    }

    // менять и удалять заметку может её автор или администратор
    private static void checkCanModify(BookingNote note, AuthenticatedUser user) {
        if (user.isAdmin()) {
            return;
        }
        if (note.getAuthor() == null || !note.getAuthor().getId().equals(user.id())) {
            throw new ForbiddenException("NOT_NOTE_AUTHOR", "Изменять и удалять заметку может только её автор или администратор");
        }
    }
}
