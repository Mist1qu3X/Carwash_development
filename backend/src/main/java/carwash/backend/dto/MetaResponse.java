package carwash.backend.dto;

import java.util.List;

// справочные значения enum для клиентов (web, Android): коды, подписи, допустимые переходы
public record MetaResponse(
        List<StatusItem> statuses,
        List<EnumItem> paymentRisks,
        List<EnumItem> roles,
        List<EnumItem> auditEventTypes,
        boolean permanentDeleteEnabled) {

    public record EnumItem(String code, String title) {
    }

    public record StatusItem(String code, String title, boolean finalStatus, List<String> next) {
    }
}
