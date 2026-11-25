package carwash.backend.domain;

// три уровня доступа; ADMIN включает права MANAGER, MANAGER — права VIEWER (см. SecurityConfig)
public enum Role {

    VIEWER("Оператор: просмотр"),
    MANAGER("Менеджер: редактирование"),
    ADMIN("Администратор");

    private final String title;

    Role(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    public String authority() {
        return "ROLE_" + name();
    }
}
