package carwash.backend.controller;

import carwash.backend.dto.NoteRequest;
import carwash.backend.dto.NoteResponse;
import carwash.backend.security.AuthenticatedUser;
import carwash.backend.service.BookingNoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/bookings/{bookingId}/notes")
@Tag(name = "Заметки к записям")
public class BookingNoteController {

    private final BookingNoteService noteService;

    public BookingNoteController(BookingNoteService noteService) {
        this.noteService = noteService;
    }

    @GetMapping
    @PreAuthorize("hasRole('VIEWER')")
    @Operation(summary = "История заметок (закреплённые сверху)")
    public List<NoteResponse> list(@PathVariable UUID bookingId) {
        return noteService.list(bookingId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Новая заметка")
    public NoteResponse create(@PathVariable UUID bookingId, @Valid @RequestBody NoteRequest request,
                               @AuthenticationPrincipal AuthenticatedUser user) {
        return noteService.create(bookingId, request, user);
    }

    @PutMapping("/{noteId}")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Изменение заметки (автор или администратор)")
    public NoteResponse update(@PathVariable UUID bookingId, @PathVariable UUID noteId,
                               @Valid @RequestBody NoteRequest request,
                               @AuthenticationPrincipal AuthenticatedUser user) {
        return noteService.update(bookingId, noteId, request, user);
    }

    @DeleteMapping("/{noteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Удаление заметки (автор или администратор)")
    public void delete(@PathVariable UUID bookingId, @PathVariable UUID noteId,
                       @AuthenticationPrincipal AuthenticatedUser user) {
        noteService.delete(bookingId, noteId, user);
    }
}
