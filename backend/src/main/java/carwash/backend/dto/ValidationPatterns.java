package carwash.backend.dto;

// регулярные выражения для Bean Validation (правила взяты из сервисов КР1)
public final class ValidationPatterns {

    private ValidationPatterns() {
    }

    // А123ВС77: 4–15 символов, буквы, цифры, пробел; хотя бы одна буква и одна цифра
    public static final String CAR_NUMBER = "^(?=.*\\p{L})(?=.*\\d)[\\p{L}0-9 ]{4,15}$";
    public static final String PHONE = "^\\+7-\\d{3}-\\d{3}-\\d{2}-\\d{2}$";
    public static final String PERSON_NAME = "^\\p{L}[\\p{L} .\\-]*$";
    public static final String BOOKING_CODE = "^[A-Za-z0-9-]{3,20}$";
    public static final String SERVICE_CODE = "^[A-Z][A-Z0-9_]{1,29}$";
    public static final String TAG = "^[\\p{L}0-9_-]{1,30}$";
}
