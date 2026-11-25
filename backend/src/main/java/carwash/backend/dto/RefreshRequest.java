package carwash.backend.dto;

import jakarta.validation.constraints.Size;

// refresh-токен в теле (Android). Web-интерфейс передаёт его в HttpOnly-cookie
public record RefreshRequest(@Size(max = 200) String refreshToken) {

    @Override
    public String toString() {
        return "RefreshRequest[***]";
    }
}
