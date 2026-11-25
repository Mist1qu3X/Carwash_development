package carwash.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// точка входа backend КР3 (REST API + web-интерфейс)
@SpringBootApplication
@ConfigurationPropertiesScan
public class CarwashBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(CarwashBackendApplication.class, args);
    }
}
