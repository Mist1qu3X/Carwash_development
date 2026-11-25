package carwash.backend.filter;

import carwash.backend.error.BadRequestException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Серверная пагинация и сортировка.
 * Сортировать можно только по полям из белого списка (имя в API -> путь в сущности),
 * размер страницы ограничен сверху.
 * Формат sort: "price,desc" или несколько полей через ";": "status,asc;scheduledAt,desc".
 */
public final class PageRequestFactory {

    public static final int DEFAULT_SIZE = 20;

    private PageRequestFactory() {
    }

    public static Pageable create(Integer page, Integer size, String sort,
                                  Map<String, String> allowedFields, Sort defaultSort, int maxSize) {
        int pageNumber = page == null || page < 0 ? 0 : page;
        int pageSize = size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, maxSize);
        Sort parsed = parseSort(sort, allowedFields);
        Sort finalSort = (parsed.isSorted() ? parsed : defaultSort).and(Sort.by(Sort.Direction.ASC, "id"));
        return PageRequest.of(pageNumber, pageSize, finalSort);
    }

    public static Sort parseSort(String sort, Map<String, String> allowedFields) {
        if (sort == null || sort.isBlank()) {
            return Sort.unsorted();
        }
        List<Sort.Order> orders = new ArrayList<>();
        for (String part : sort.split(";")) {
            if (part.isBlank()) {
                continue;
            }
            String[] pieces = part.trim().split(",");
            String field = pieces[0].trim();
            String property = allowedFields.get(field);
            if (property == null) {
                throw new BadRequestException("BAD_SORT",
                        "Сортировка по полю '" + field + "' не поддерживается. Доступно: " + String.join(", ", allowedFields.keySet()));
            }
            Sort.Direction direction = Sort.Direction.ASC;
            if (pieces.length > 1) {
                String dir = pieces[1].trim().toLowerCase(Locale.ROOT);
                if (dir.equals("desc")) {
                    direction = Sort.Direction.DESC;
                } else if (!dir.equals("asc")) {
                    throw new BadRequestException("BAD_SORT", "Направление сортировки должно быть asc или desc");
                }
            }
            orders.add(new Sort.Order(direction, property));
        }
        return Sort.by(orders);
    }
}
