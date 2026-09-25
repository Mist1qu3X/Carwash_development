package carwash.repository;

import carwash.exception.DatabaseException;
import carwash.model.Booking;
import carwash.model.BookingStatus;
import carwash.model.ServiceType;
import carwash.util.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

<<<<<<< HEAD
// работа с таблицей clients через JDBC (везде PreparedStatement с параметрами).
public class ClientRepository implements Repository<Client> {
=======
// работа с таблицей bookings через JDBC
public class BookingRepository implements Repository<Booking> {

    private static final String SELECT = "SELECT b.id, b.client_id, b.car_number, "
            + "b.car_model, b.service_type, b.status, b.scheduled_at, b.price, "
            + "b.created_at, c.full_name AS client_name "
            + "FROM bookings b JOIN clients c ON c.id = b.client_id ";
>>>>>>> 4cae2f366d2c8f4e3fab74f5bed32f853c461031

    @Override
    public Booking save(Booking booking) {
        String sql = "INSERT INTO bookings (client_id, car_number, car_model, service_type, "
                + "status, scheduled_at, price) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            setBookingParameters(statement, booking, false);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    booking.setId(keys.getInt(1));
                }
            }
            return booking;
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка при сохранении записи.", e);
        }
    }

    @Override
    public List<Booking> findAll() {
        return query(SELECT + "ORDER BY b.id", statement -> {
        });
    }

    @Override
    public Optional<Booking> findById(int id) {
        List<Booking> result = query(SELECT + "WHERE b.id = ?", statement -> statement.setInt(1, id));
        return result.stream().findFirst();
    }

    @Override
    public void update(Booking booking) {
        String sql = "UPDATE bookings SET car_number = ?, car_model = ?, service_type = ?, "
                + "status = ?, scheduled_at = ?, price = ? WHERE id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, booking.getCarNumber());
            statement.setString(2, booking.getCarModel());
            statement.setString(3, booking.getServiceType().name());
            statement.setString(4, booking.getStatus().name());
            statement.setTimestamp(5, Timestamp.valueOf(booking.getScheduledAt()));
            statement.setDouble(6, booking.getPrice());
            statement.setInt(7, booking.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка при обновлении записи.", e);
        }
    }

    @Override
    public void deleteById(int id) {
        String sql = "DELETE FROM bookings WHERE id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка при удалении записи.", e);
        }
    }

    public List<Booking> searchByCarNumber(String carNumber) {
        return query(SELECT + "WHERE LOWER(b.car_number) LIKE LOWER(?) ORDER BY b.id",
                statement -> statement.setString(1, "%" + carNumber.trim() + "%"));
    }

    public List<Booking> searchByClientName(String name) {
        return query(SELECT + "WHERE LOWER(c.full_name) LIKE LOWER(?) ORDER BY b.id",
                statement -> statement.setString(1, "%" + name.trim() + "%"));
    }

    public List<Booking> filterByStatus(BookingStatus status) {
        return query(SELECT + "WHERE b.status = ? ORDER BY b.id",
                statement -> statement.setString(1, status.name()));
    }

    public List<Booking> filterByServiceType(ServiceType type) {
        return query(SELECT + "WHERE b.service_type = ? ORDER BY b.id",
                statement -> statement.setString(1, type.name()));
    }

    public List<Booking> filterByDateRange(LocalDateTime from, LocalDateTime to) {
        return query(SELECT + "WHERE b.scheduled_at BETWEEN ? AND ? ORDER BY b.scheduled_at",
                statement -> {
                    statement.setTimestamp(1, Timestamp.valueOf(from));
                    statement.setTimestamp(2, Timestamp.valueOf(to));
                });
    }

    private List<Booking> query(String sql, StatementConfigurer configurer) {
        List<Booking> result = new ArrayList<>();
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            configurer.configure(statement);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка при получении записей.", e);
        }
    }

    private void setBookingParameters(PreparedStatement statement, Booking booking, boolean includeStatus)
            throws SQLException {
        statement.setInt(1, booking.getClientId());
        statement.setString(2, booking.getCarNumber());
        statement.setString(3, booking.getCarModel());
        statement.setString(4, booking.getServiceType().name());
        int dateIndex;
        if (includeStatus) {
            statement.setString(5, booking.getStatus().name());
            dateIndex = 6;
        } else {
            statement.setString(5, booking.getStatus().name());
            dateIndex = 6;
        }
        statement.setTimestamp(dateIndex, Timestamp.valueOf(booking.getScheduledAt()));
        statement.setDouble(dateIndex + 1, booking.getPrice());
    }

    private Booking mapRow(ResultSet rs) throws SQLException {
        Timestamp scheduled = rs.getTimestamp("scheduled_at");
        Timestamp created = rs.getTimestamp("created_at");
        Booking booking = new Booking(
                rs.getInt("id"),
                rs.getInt("client_id"),
                rs.getString("car_number"),
                rs.getString("car_model"),
                ServiceType.valueOf(rs.getString("service_type")),
                BookingStatus.valueOf(rs.getString("status")),
                scheduled.toLocalDateTime(),
                rs.getDouble("price"),
                created == null ? null : created.toLocalDateTime());
        booking.setClientName(rs.getString("client_name"));
        return booking;
    }

    @FunctionalInterface
    private interface StatementConfigurer {
        void configure(PreparedStatement statement) throws SQLException;
    }
}