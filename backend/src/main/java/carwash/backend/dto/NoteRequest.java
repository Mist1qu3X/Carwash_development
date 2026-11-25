package carwash.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record NoteRequest(
        @NotBlank(message = "Текст заметки не может быть пустым")
        @Size(max = 2000, message = "Заметка — не длиннее 2000 символов")
        String text,
        boolean pinned,
        @PositiveOrZero Long version) {
}
