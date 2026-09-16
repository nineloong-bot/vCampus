package edu.seu.vcampus.server.library.repository;

import edu.seu.vcampus.common.library.AdminReservationSearchQuery;
import edu.seu.vcampus.common.library.BookReservationView;
import edu.seu.vcampus.common.library.ReservationStatus;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.server.library.domain.BookReservation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.NoSuchElementException;

/** UCanAccess implementation of reservation queue persistence and reads. */
public final class AccessReservationRepository implements ReservationRepository {
    private static final String SELECT_JOINED = "SELECT r.*, b.title, c.barcode, u.loginId "
            + "FROM ((tblBookReservation r INNER JOIN tblBookCopy c ON r.copyId = c.copyId) "
            + "INNER JOIN tblBook b ON r.bookId = b.bookId) "
            + "LEFT JOIN tblUser u ON r.userId = u.userId";

    @Override
    public BookReservation insert(Connection connection, BookReservation reservation)
            throws SQLException {
        String sql = "INSERT INTO tblBookReservation (reservationId, copyId, bookId, userId, "
                + "reserverRoleCode, reservedAt, queueOrder, reservationStatus, readyAt, expiresAt, "
                + "rowVersion) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, reservation.reservationId());
            statement.setString(2, reservation.copyId());
            statement.setString(3, reservation.bookId());
            statement.setString(4, reservation.userId());
            statement.setString(5, reservation.reserverRoleCode());
            statement.setTimestamp(6, Timestamp.from(reservation.reservedAt()));
            statement.setLong(7, reservation.queueOrder());
            statement.setString(8, reservation.status().name());
            setInstant(statement, 9, reservation.readyAt());
            setInstant(statement, 10, reservation.expiresAt());
            statement.setLong(11, reservation.rowVersion());
            statement.executeUpdate();
            return reservation;
        }
    }

    @Override
    public BookReservation require(Connection connection, String reservationId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM tblBookReservation WHERE reservationId = ?")) {
            statement.setString(1, reservationId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new NoSuchElementException("Reservation not found: " + reservationId);
                }
                return read(result);
            }
        }
    }

    @Override
    public void update(Connection connection, BookReservation reservation, long expectedVersion)
            throws SQLException {
        String sql = "UPDATE tblBookReservation SET reservationStatus = ?, readyAt = ?, "
                + "expiresAt = ?, rowVersion = rowVersion + 1 WHERE reservationId = ? AND rowVersion = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, reservation.status().name());
            setInstant(statement, 2, reservation.readyAt());
            setInstant(statement, 3, reservation.expiresAt());
            statement.setString(4, reservation.reservationId());
            statement.setLong(5, expectedVersion);
            if (statement.executeUpdate() != 1) {
                throw new ConcurrentModificationException(
                        "Reservation changed: " + reservation.reservationId());
            }
        }
    }

    @Override
    public List<BookReservation> findOpenForCopy(Connection connection, String copyId)
            throws SQLException {
        String sql = "SELECT * FROM tblBookReservation WHERE copyId = ? "
                + "AND reservationStatus IN ('WAITING', 'READY') ORDER BY queueOrder";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, copyId);
            try (ResultSet result = statement.executeQuery()) {
                List<BookReservation> records = new ArrayList<>();
                while (result.next()) records.add(read(result));
                return records;
            }
        }
    }

    @Override
    public boolean hasOpenForUserAndCopy(Connection connection, String copyId, String userId)
            throws SQLException {
        String sql = "SELECT COUNT(*) FROM tblBookReservation WHERE copyId = ? AND userId = ? "
                + "AND reservationStatus IN ('WAITING', 'READY')";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, copyId);
            statement.setString(2, userId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1) > 0;
            }
        }
    }

    @Override
    public int countOpenForUser(Connection connection, String userId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM tblBookReservation WHERE userId = ? "
                + "AND reservationStatus IN ('WAITING', 'READY')";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    @Override
    public long nextQueueOrder(Connection connection, String copyId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT MAX(queueOrder) FROM tblBookReservation WHERE copyId = ?")) {
            statement.setString(1, copyId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                long current = result.getLong(1);
                return result.wasNull() ? 1L : current + 1L;
            }
        }
    }

    @Override
    public List<BookReservation> markExpiredReady(Connection connection, Instant now)
            throws SQLException {
        String sql = "SELECT * FROM tblBookReservation WHERE reservationStatus = 'READY' "
                + "AND expiresAt IS NOT NULL AND expiresAt < ?";
        List<BookReservation> expired = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(now));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) expired.add(read(result));
            }
        }
        List<BookReservation> updated = new ArrayList<>();
        for (BookReservation reservation : expired) {
            BookReservation expiredReservation = new BookReservation(reservation.reservationId(),
                    reservation.copyId(), reservation.bookId(), reservation.userId(),
                    reservation.reserverRoleCode(), reservation.reservedAt(), reservation.queueOrder(),
                    ReservationStatus.EXPIRED, reservation.readyAt(), reservation.expiresAt(),
                    reservation.rowVersion() + 1);
            update(connection, expiredReservation, reservation.rowVersion());
            updated.add(expiredReservation);
        }
        return updated;
    }

    @Override
    public List<BookReservationView> findForUser(Connection connection, String userId, Instant now)
            throws SQLException {
        String sql = SELECT_JOINED + " WHERE r.userId = ? ORDER BY r.reservedAt DESC";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            return readViews(connection, statement.executeQuery(), now);
        }
    }

    @Override
    public PageResult<BookReservationView> searchAll(Connection connection,
            AdminReservationSearchQuery query, Instant now) throws SQLException {
        requirePage(query.page(), query.pageSize());
        StringBuilder sql = new StringBuilder(SELECT_JOINED).append(" WHERE 1 = 1");
        List<String> values = new ArrayList<>();
        if (query.keyword() != null && !query.keyword().isBlank()) {
            String pattern = "%" + query.keyword().trim() + "%";
            sql.append(" AND (b.title LIKE ? OR c.barcode LIKE ? OR u.loginId LIKE ? OR r.userId LIKE ?)");
            for (int index = 0; index < 4; index++) values.add(pattern);
        }
        sql.append(" ORDER BY r.reservedAt DESC");
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int index = 0; index < values.size(); index++) statement.setString(index + 1, values.get(index));
            List<BookReservationView> records = readViews(connection, statement.executeQuery(), now).stream()
                    .filter(view -> query.status() == null || view.status() == query.status())
                    .toList();
            int from = Math.min((query.page() - 1) * query.pageSize(), records.size());
            int to = Math.min(from + query.pageSize(), records.size());
            return new PageResult<>(records.subList(from, to), query.page(), query.pageSize(), records.size());
        }
    }

    private List<BookReservationView> readViews(Connection connection, ResultSet result, Instant now)
            throws SQLException {
        try (result) {
            List<BookReservationView> views = new ArrayList<>();
            while (result.next()) {
                BookReservation reservation = read(result);
                if (reservation.status() == ReservationStatus.READY && reservation.expiresAt() != null
                        && reservation.expiresAt().isBefore(now)) {
                    reservation = new BookReservation(reservation.reservationId(), reservation.copyId(),
                            reservation.bookId(), reservation.userId(), reservation.reserverRoleCode(),
                            reservation.reservedAt(), reservation.queueOrder(), ReservationStatus.EXPIRED,
                            reservation.readyAt(), reservation.expiresAt(), reservation.rowVersion());
                }
                int position = 0;
                if (reservation.status() == ReservationStatus.WAITING
                        || reservation.status() == ReservationStatus.READY) {
                    position = queuePosition(connection, reservation.copyId(), reservation.queueOrder());
                }
                views.add(new BookReservationView(reservation.reservationId(), reservation.copyId(),
                        reservation.bookId(), reservation.userId(), result.getString("loginId"),
                        result.getString("title"), result.getString("barcode"), reservation.status(),
                        reservation.reservedAt(), reservation.readyAt(), reservation.expiresAt(),
                        position, reservation.rowVersion()));
            }
            return views;
        }
    }

    @Override public int queuePosition(Connection connection, String copyId, long queueOrder)
            throws SQLException {
        String sql = "SELECT COUNT(*) FROM tblBookReservation WHERE copyId = ? "
                + "AND reservationStatus IN ('WAITING', 'READY') AND queueOrder <= ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, copyId);
            statement.setLong(2, queueOrder);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private static BookReservation read(ResultSet result) throws SQLException {
        Timestamp readyAt = result.getTimestamp("readyAt");
        Timestamp expiresAt = result.getTimestamp("expiresAt");
        return new BookReservation(result.getString("reservationId"), result.getString("copyId"),
                result.getString("bookId"), result.getString("userId"),
                result.getString("reserverRoleCode"), result.getTimestamp("reservedAt").toInstant(),
                result.getLong("queueOrder"),
                ReservationStatus.valueOf(result.getString("reservationStatus")),
                readyAt == null ? null : readyAt.toInstant(),
                expiresAt == null ? null : expiresAt.toInstant(), result.getLong("rowVersion"));
    }

    private static void setInstant(PreparedStatement statement, int index, Instant value)
            throws SQLException {
        if (value == null) statement.setNull(index, Types.TIMESTAMP);
        else statement.setTimestamp(index, Timestamp.from(value));
    }

    private static void requirePage(int page, int pageSize) {
        if (page < 1 || pageSize < 1) {
            throw new IllegalArgumentException("Page and page size must be positive");
        }
    }
}
