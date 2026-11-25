package carwash.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Интеграционный тест REST API на H2: авторизация, роли, CRUD, архив, массовые операции,
 * заметки, CSV, аналитика, аудит. Методы выполняются по порядку, т.к. используют общие данные.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@test.local";
    private static final String ADMIN_TEMP_PASSWORD = "Temp!Passw0rd-2026";
    private static final String ADMIN_PASSWORD = "Moika!Secure-2026";
    private static final String USER_PASSWORD = "Strong!Password-77";

    private static String adminToken;
    private static String serviceTypeId;
    private static String clientId;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Test
    @Order(1)
    void requestWithoutTokenReturns401InUnifiedFormat() throws Exception {
        mvc.perform(get("/api/bookings"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    @Order(2)
    void temporaryPasswordMustBeChangedBeforeWork() throws Exception {
        JsonNode login = login(ADMIN_EMAIL, ADMIN_TEMP_PASSWORD);
        assertThat(login.path("user").path("mustChangePassword").asBoolean()).isTrue();
        assertThat(login.path("refreshToken").asText()).isNotBlank();
        String token = login.path("accessToken").asText();

        mvc.perform(auth(get("/api/bookings"), token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("PASSWORD_CHANGE_REQUIRED"));
        mvc.perform(auth(get("/api/auth/me"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(ADMIN_EMAIL))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        // слабый пароль отклоняется политикой
        mvc.perform(auth(post("/api/auth/change-password"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("currentPassword", ADMIN_TEMP_PASSWORD, "newPassword", "weakpassword1"))))
                .andExpect(status().isBadRequest());

        JsonNode changed = changePassword(token, ADMIN_TEMP_PASSWORD, ADMIN_PASSWORD);
        assertThat(changed.path("user").path("mustChangePassword").asBoolean()).isFalse();
        adminToken = changed.path("accessToken").asText();

        mvc.perform(auth(get("/api/bookings"), adminToken)).andExpect(status().isOk());
    }

    @Test
    @Order(3)
    void rolesHaveDifferentRights() throws Exception {
        String viewer = createUserAndLogin("viewer@test.local", "Оператор Тест", List.of("VIEWER"));
        String manager = createUserAndLogin("manager@test.local", "Менеджер Тест", List.of("MANAGER"));

        mvc.perform(auth(get("/api/bookings"), viewer)).andExpect(status().isOk());
        mvc.perform(auth(post("/api/clients"), viewer).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("fullName", "Пётр Иванов", "phone", "+7-900-000-00-01"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        mvc.perform(auth(get("/api/admin/users"), viewer)).andExpect(status().isForbidden());

        // менеджер редактирует данные, но не справочник и не пользователей
        mvc.perform(auth(post("/api/clients"), manager).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("fullName", "Пётр Иванов", "phone", "+7-900-000-00-01"))))
                .andExpect(status().isCreated());
        mvc.perform(auth(post("/api/service-types"), manager).contentType(MediaType.APPLICATION_JSON)
                        .content(body(serviceTypeBody("MANAGER_TRY", "Попытка менеджера"))))
                .andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/admin/audit"), manager)).andExpect(status().isForbidden());
    }

    @Test
    @Order(4)
    void bookingCrudArchiveAndVersionConflict() throws Exception {
        serviceTypeId = read(mvc.perform(auth(post("/api/service-types"), adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(serviceTypeBody("BODY_WASH", "Мойка кузова"))))
                .andExpect(status().isCreated())).path("id").asText();
        clientId = read(mvc.perform(auth(post("/api/clients"), adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("fullName", "Иван Петров", "phone", "+7-900-111-11-11", "email", "ivan@mail.ru"))))
                .andExpect(status().isCreated())).path("id").asText();

        // неверные данные -> 400 с подробностями по полям
        mvc.perform(auth(post("/api/bookings"), adminToken).contentType(MediaType.APPLICATION_JSON)
                        .content(body(bookingBody("123", "-5", null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.length()").value(2));

        JsonNode created = read(mvc.perform(auth(post("/api/bookings"), adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(bookingBody("а123вс77", "3500", null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code", startsWith("CW-")))
                .andExpect(jsonPath("$.carNumber").value("А123ВС77"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.paymentRisk").value("HIGH")));
        String id = created.path("id").asText();
        long version = created.path("version").asLong();

        mvc.perform(auth(get("/api/bookings/" + id), adminToken)).andExpect(status().isOk());
        mvc.perform(auth(get("/api/bookings/" + UUID.randomUUID()), adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));

        // устаревшая версия -> 409
        mvc.perform(auth(put("/api/bookings/" + id), adminToken).contentType(MediaType.APPLICATION_JSON)
                        .content(body(bookingBody("А123ВС77", "3500", "CONFIRMED", version + 5))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("VERSION_CONFLICT"));

        JsonNode updated = read(mvc.perform(auth(put("/api/bookings/" + id), adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(bookingBody("А123ВС77", "3500", "CONFIRMED", version))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED")));
        version = updated.path("version").asLong();

        // правило КР1: из "Подтверждена" сразу в "Завершена" нельзя
        mvc.perform(auth(put("/api/bookings/" + id), adminToken).contentType(MediaType.APPLICATION_JSON)
                        .content(body(bookingBody("А123ВС77", "3500", "COMPLETED", version))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_STATUS_TRANSITION"));

        // окончательно удалить можно только архивную запись
        mvc.perform(auth(delete("/api/bookings/" + id + "/permanent"), adminToken)).andExpect(status().isConflict());
        mvc.perform(auth(delete("/api/bookings/" + id), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));
        mvc.perform(auth(get("/api/bookings").param("archive", "ARCHIVED"), adminToken))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(auth(post("/api/bookings/" + id + "/restore"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(false));
        mvc.perform(auth(delete("/api/bookings/" + id), adminToken)).andExpect(status().isOk());
        mvc.perform(auth(delete("/api/bookings/" + id + "/permanent"), adminToken)).andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/bookings/" + id), adminToken)).andExpect(status().isNotFound());
    }

    @Test
    @Order(5)
    void notesBulkOperationsAndAnalytics() throws Exception {
        String first = createBooking("Т555ММ97", "1200");
        String second = createBooking("О321УК77", "500");

        JsonNode note = read(mvc.perform(auth(post("/api/bookings/" + first + "/notes"), adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("text", "Позвонить за час", "pinned", true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorName").value("Test Admin")));
        mvc.perform(auth(put("/api/bookings/" + first + "/notes/" + note.path("id").asText()), adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("text", "Позвонить за два часа", "pinned", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pinned").value(false));
        mvc.perform(auth(get("/api/bookings/" + first + "/notes"), adminToken))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].text").value("Позвонить за два часа"));
        mvc.perform(auth(delete("/api/bookings/" + first + "/notes/" + note.path("id").asText()), adminToken))
                .andExpect(status().isNoContent());

        // массовая смена статуса: две записи успешно, несуществующая — в списке ошибок
        String missing = UUID.randomUUID().toString();
        mvc.perform(auth(post("/api/bookings/bulk"), adminToken).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("ids", List.of(first, second, missing), "action", "CHANGE_STATUS", "status", "CONFIRMED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.succeededCount").value(2))
                .andExpect(jsonPath("$.failedCount").value(1))
                .andExpect(jsonPath("$.failed[0].id").value(missing))
                .andExpect(jsonPath("$.failed[0].error").value("NOT_FOUND"));
        mvc.perform(auth(post("/api/bookings/bulk"), adminToken).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("ids", List.of(first, second), "action", "ADD_TAG", "tag", "VIP"))))
                .andExpect(jsonPath("$.succeededCount").value(2));
        mvc.perform(auth(get("/api/bookings").param("tag", "vip"), adminToken))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(auth(post("/api/bookings/bulk"), adminToken).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("ids", List.of(first), "action", "ARCHIVE"))))
                .andExpect(jsonPath("$.succeededCount").value(1));

        mvc.perform(auth(get("/api/analytics/summary"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.active").value(1))
                .andExpect(jsonPath("$.archived").value(1))
                .andExpect(jsonPath("$.byStatus.length()").value(5))
                .andExpect(jsonPath("$.byPaymentRisk.length()").value(3));
    }

    @Test
    @Order(6)
    void csvExportPreviewAndApply() throws Exception {
        mvc.perform(auth(get("/api/bookings/export").param("archive", "ALL"), adminToken))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(containsString("code;clientName;clientPhone")))
                // защита от CSV-инъекции: телефон начинается с '+', поэтому экранирован
                .andExpect(content().string(containsString("'+7-900-111-11-11")));

        String date = LocalDateTime.now().plusDays(20).truncatedTo(ChronoUnit.MINUTES)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        String csv = "code;clientName;clientPhone;carNumber;carModel;serviceType;status;scheduledAt;price;paid;tags;comment\n"
                + "IMP-001;Новый Клиент;+7-900-777-77-77;Х777ХХ77;Lada Granta;BODY_WASH;CREATED;" + date + ";;нет;новый;\n"
                + "IMP-002;Иван Петров;+7-900-111-11-11;BAD;;BODY_WASH;CREATED;вчера;abc;да;;\n"
                + "IMP-003;;+7-900-000-99-99;М001ММ77;;UNKNOWN;;" + date + ";500;;;\n";
        MockMultipartFile file = new MockMultipartFile("file", "bookings.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        long before = read(mvc.perform(auth(get("/api/bookings").param("archive", "ALL"), adminToken))).path("totalElements").asLong();

        mvc.perform(auth(multipart("/api/bookings/import/preview").file(file).param("mode", "CREATE_ONLY"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(true))
                .andExpect(jsonPath("$.totalRows").value(3))
                .andExpect(jsonPath("$.validRows").value(1))
                .andExpect(jsonPath("$.invalidRows").value(2))
                .andExpect(jsonPath("$.rows[0].action").value("CREATE"))
                .andExpect(jsonPath("$.rows[1].action").value("ERROR"));
        // предварительная проверка ничего не сохраняет
        mvc.perform(auth(get("/api/bookings").param("archive", "ALL"), adminToken))
                .andExpect(jsonPath("$.totalElements").value(before));
        mvc.perform(auth(get("/api/clients").param("q", "777-77"), adminToken))
                .andExpect(jsonPath("$.totalElements").value(0));

        mvc.perform(auth(multipart("/api/bookings/import/apply").file(file).param("mode", "CREATE_ONLY"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(false))
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.invalidRows").value(2));
        mvc.perform(auth(get("/api/bookings").param("q", "IMP-001"), adminToken))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].price").value(500.0))
                .andExpect(jsonPath("$.content[0].client.fullName").value("Новый Клиент"));

        // повторно в режиме "только новые" — ошибка; в режиме обновления по коду — UPDATE
        String update = "code;clientPhone;carNumber;serviceType;status;scheduledAt;price;paid\n"
                + "IMP-001;+7-900-777-77-77;Х777ХХ77;BODY_WASH;CONFIRMED;" + date + ";650;да\n";
        MockMultipartFile updateFile = new MockMultipartFile("file", "update.csv", "text/csv", update.getBytes(StandardCharsets.UTF_8));
        mvc.perform(auth(multipart("/api/bookings/import/preview").file(updateFile).param("mode", "CREATE_ONLY"), adminToken))
                .andExpect(jsonPath("$.invalidRows").value(1));
        mvc.perform(auth(multipart("/api/bookings/import/apply").file(updateFile).param("mode", "UPSERT"), adminToken))
                .andExpect(jsonPath("$.updated").value(1));
        mvc.perform(auth(get("/api/bookings").param("q", "IMP-001"), adminToken))
                .andExpect(jsonPath("$.content[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.content[0].paid").value(true))
                .andExpect(jsonPath("$.content[0].paymentRisk").value("LOW"));
    }

    @Test
    @Order(7)
    void refreshRotationAndLogout() throws Exception {
        JsonNode login = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String refresh = login.path("refreshToken").asText();

        JsonNode refreshed = read(mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("refreshToken", refresh))))
                .andExpect(status().isOk()));
        String newRefresh = refreshed.path("refreshToken").asText();
        assertThat(newRefresh).isNotEqualTo(refresh);

        // старый токен уже отозван: повторное использование -> 401 и отзыв всех сессий
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("refreshToken", refresh))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("REFRESH_TOKEN_REVOKED"));
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("refreshToken", newRefresh))))
                .andExpect(status().isUnauthorized());

        JsonNode again = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        mvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("refreshToken", again.path("refreshToken").asText()))))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("refreshToken", again.path("refreshToken").asText()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(8)
    void bruteForceLocksAccountAndDisabledUserCannotLogin() throws Exception {
        String userId = createUser("victim@test.local", "Жертва Перебора", List.of("VIEWER")).path("user").path("id").asText();
        for (int i = 1; i <= 4; i++) {
            loginRaw("victim@test.local", "Wrong!Password-" + i).andExpect(status().isUnauthorized());
        }
        loginRaw("victim@test.local", "Wrong!Password-5").andExpect(status().isTooManyRequests());
        loginRaw("victim@test.local", "Wrong!Password-6")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("TOO_MANY_ATTEMPTS"));

        // сброс пароля администратором снимает блокировку
        JsonNode reset = read(mvc.perform(auth(post("/api/admin/users/" + userId + "/reset-password"), adminToken))
                .andExpect(status().isOk()));
        String temp = reset.path("temporaryPassword").asText();
        loginRaw("victim@test.local", temp).andExpect(status().isOk());

        // после входа version пользователя изменился — берём актуальную карточку
        JsonNode user = read(mvc.perform(auth(get("/api/admin/users/" + userId), adminToken)));
        mvc.perform(auth(put("/api/admin/users/" + userId), adminToken).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", "victim@test.local", "fullName", "Жертва Перебора",
                                "roles", List.of("VIEWER"), "active", false, "version", user.path("version").asLong()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        loginRaw("victim@test.local", temp)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("ACCOUNT_DISABLED"));
    }

    @Test
    @Order(9)
    void auditIsWrittenAndSearchable() throws Exception {
        mvc.perform(auth(get("/api/admin/audit").param("eventType", "LOGIN_SUCCESS").param("size", "5"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].eventType").value("LOGIN_SUCCESS"))
                .andExpect(jsonPath("$.content[0].correlationId").isNotEmpty())
                .andExpect(jsonPath("$.size").value(5));
        mvc.perform(auth(get("/api/admin/audit").param("q", "Массовая операция"), adminToken))
                .andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(auth(get("/api/admin/audit").param("eventType", "LOGIN_LOCKED"), adminToken))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(auth(get("/api/admin/users").param("q", "test.local").param("sort", "email,asc"), adminToken))
                .andExpect(jsonPath("$.content[0].email").value(ADMIN_EMAIL));
    }

    // ---------- вспомогательные методы ----------

    private String createBooking(String carNumber, String price) throws Exception {
        return read(mvc.perform(auth(post("/api/bookings"), adminToken).contentType(MediaType.APPLICATION_JSON)
                        .content(body(bookingBody(carNumber, price, null, null))))
                .andExpect(status().isCreated())).path("id").asText();
    }

    private Map<String, Object> bookingBody(String carNumber, String price, String status, Long version) {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("clientId", clientId);
        map.put("serviceTypeId", serviceTypeId);
        map.put("carNumber", carNumber);
        map.put("carModel", "Toyota Camry");
        map.put("scheduledAt", LocalDateTime.now().plusDays(7).truncatedTo(ChronoUnit.MINUTES).toString());
        map.put("price", price);
        map.put("paid", false);
        map.put("tags", List.of("постоянный"));
        if (status != null) {
            map.put("status", status);
        }
        if (version != null) {
            map.put("version", version);
        }
        return map;
    }

    private static Map<String, Object> serviceTypeBody(String code, String name) {
        return Map.of("code", code, "name", name, "basePrice", 500, "durationMinutes", 30);
    }

    private JsonNode createUser(String email, String name, List<String> roles) throws Exception {
        return read(mvc.perform(auth(post("/api/admin/users"), adminToken).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", email, "fullName", name, "roles", roles))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.mustChangePassword").value(true)));
    }

    private String createUserAndLogin(String email, String name, List<String> roles) throws Exception {
        String temp = createUser(email, name, roles).path("temporaryPassword").asText();
        String token = login(email, temp).path("accessToken").asText();
        return changePassword(token, temp, USER_PASSWORD).path("accessToken").asText();
    }

    private JsonNode changePassword(String token, String current, String next) throws Exception {
        return read(mvc.perform(auth(post("/api/auth/change-password"), token).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("currentPassword", current, "newPassword", next))))
                .andExpect(status().isOk()));
    }

    private JsonNode login(String email, String password) throws Exception {
        return read(loginRaw(email, password).andExpect(status().isOk()));
    }

    private ResultActions loginRaw(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("email", email, "password", password))));
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder builder, String token) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private String body(Object value) throws Exception {
        return json.writeValueAsString(value);
    }

    private JsonNode read(ResultActions actions) throws Exception {
        return json.readTree(actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
