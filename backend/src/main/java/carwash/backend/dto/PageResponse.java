package carwash.backend.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

// страница результатов: содержимое + данные для пагинации
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages, String sort) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getSort().toString());
    }
}
