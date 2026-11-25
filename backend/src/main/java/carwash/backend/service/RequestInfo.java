package carwash.backend.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;

// IP и User-Agent клиента для refresh-сессии
public record RequestInfo(String ipAddress, String userAgent) {

    public static RequestInfo from(HttpServletRequest request) {
        String agent = request.getHeader(HttpHeaders.USER_AGENT);
        if (agent != null && agent.length() > 255) {
            agent = agent.substring(0, 255);
        }
        return new RequestInfo(request.getRemoteAddr(), agent);
    }
}
