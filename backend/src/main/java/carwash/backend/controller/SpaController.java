package carwash.backend.controller;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * React-клиент использует BrowserRouter: адреса /bookings, /clients и т.д. существуют только
 * в браузере. Если пользователь обновит страницу, сервер должен отдать index.html,
 * а дальше маршрут разберёт React Router.
 */
@Hidden
@Controller
public class SpaController {

    @GetMapping({"/bookings", "/clients", "/analytics", "/services", "/users", "/audit", "/profile"})
    public String forwardToReact() {
        return "forward:/index.html";
    }
}
