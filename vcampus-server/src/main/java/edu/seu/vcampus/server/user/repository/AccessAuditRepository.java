package edu.seu.vcampus.server.user.repository;

import edu.seu.vcampus.server.persistence.PersistenceException;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

/** Access JDBC implementation that appends immutable security audit events. */
public final class AccessAuditRepository implements AuditRepository {
    private static final String INSERT_SQL = """
            INSERT INTO tblAuditLog
                (auditId, userId, actionCode, targetType, targetId,
                 resultCode, clientAddress, createdAt)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    @Override
    public void record(Connection connection, String userId, String actionCode,
                       String targetType, String targetId, String resultCode) {
        try (var statement = connection.prepareStatement(INSERT_SQL)) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, userId);
            statement.setString(3, actionCode);
            statement.setString(4, targetType);
            statement.setString(5, targetId);
            statement.setString(6, resultCode);
            statement.setString(7, null);
            statement.setTimestamp(8, Timestamp.valueOf(LocalDateTime.now()));
            statement.executeUpdate();
        } catch (SQLException error) {
            throw new PersistenceException("Could not record security audit event", error);
        }
    }
}
