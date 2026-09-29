package carwash.util;

import carwash.exception.DatabaseException;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

// подключение к базе PostgreSQL
public class DatabaseManager {

    private static final Properties CONFIG = loadConfig();
    private static final String URL = setting(
            "CARWASH_DB_URL", "url", "jdbc:postgresql://localhost:5432/carwash");
    private static final String USER = setting("CARWASH_DB_USER", "user", "postgres");
    private static final String PASSWORD = setting("CARWASH_DB_PASSWORD", "password", "");

    private DatabaseManager() {
    }

    public static Connection getConnection() {
        try {
            return DriverManager.getConnection(URL, USER, PASSWORD);
        } catch (SQLException e) {
            throw new DatabaseException(
                    "Не удалось подключиться к базе данных. Проверьте, что PostgreSQL "
                            + "запущен и параметры подключения указаны верно.", e);
        }
    }

    private static Properties loadConfig() {
        Properties properties = new Properties();
        Path configPath = Path.of(System.getProperty("user.home"), ".carwash.properties");
        if (Files.isRegularFile(configPath)) {
            try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
                properties.load(reader);
            } catch (IOException e) {
                throw new ExceptionInInitializerError(e);
            }
        }
        return properties;
    }

    private static String setting(String environmentName, String propertyName, String defaultValue) {
        String environmentValue = System.getenv(environmentName);
        if (environmentValue != null && !environmentValue.isBlank()) {
            return environmentValue;
        }
        return CONFIG.getProperty(propertyName, defaultValue);
    }
}