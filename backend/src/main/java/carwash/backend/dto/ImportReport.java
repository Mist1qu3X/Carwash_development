package carwash.backend.dto;

import java.util.List;

/**
 * Результат проверки (dryRun = true) или применения (dryRun = false) CSV.
 * rows — построчный результат (не больше 1000 строк в ответе).
 */
public record ImportReport(
        ImportMode mode,
        boolean dryRun,
        int totalRows,
        int validRows,
        int invalidRows,
        int created,
        int updated,
        List<RowResult> rows,
        boolean truncated) {

    public enum RowAction {
        CREATE,
        UPDATE,
        ERROR
    }

    public record RowResult(long line, String code, RowAction action, String message) {
    }
}
