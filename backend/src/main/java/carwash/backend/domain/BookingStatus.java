package carwash.backend.domain;

// статусы записи и правила переходов между ними (перенесено из КР1)
public enum BookingStatus {

    CREATED("Создана"),
    CONFIRMED("Подтверждена"),
    IN_PROGRESS("В работе"),
    COMPLETED("Завершена"),
    CANCELLED("Отменена");

    private final String title;

    BookingStatus(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    // можно ли сменить текущий статус на target (тот же статус — можно, это не переход)
    public boolean canChangeTo(BookingStatus target) {
        if (target == this) {
            return true;
        }
        return switch (this) {
            case CREATED -> target == CONFIRMED || target == CANCELLED;
            case CONFIRMED -> target == IN_PROGRESS || target == CANCELLED;
            case IN_PROGRESS -> target == COMPLETED || target == CANCELLED;
            case COMPLETED, CANCELLED -> false; // завершённую и отменённую менять нельзя
        };
    }

    public boolean isFinal() {
        return this == COMPLETED || this == CANCELLED;
    }

    // запись ещё только запланирована (машина не приехала)
    public boolean isPlanned() {
        return this == CREATED || this == CONFIRMED;
    }
}
