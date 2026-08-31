package edu.seu.vcampus.server.user.repository;

import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.user.SecurityAuditQuery;
import edu.seu.vcampus.common.user.SecurityAuditView;
import edu.seu.vcampus.server.persistence.PersistenceException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
    public void record(Connection connection, String actorUserId, String actionCode,
                       String targetType, String targetId, String resultCode,
                       String clientAddress) {
        try (var statement = connection.prepareStatement(INSERT_SQL)) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, actorUserId);
            statement.setString(3, actionCode);
            statement.setString(4, targetType);
            statement.setString(5, targetId);
            statement.setString(6, resultCode);
            statement.setString(7, clientAddress);
            statement.setTimestamp(8, Timestamp.valueOf(LocalDateTime.now()));
            statement.executeUpdate();
        } catch (SQLException error) {
            throw new PersistenceException("Could not record security audit event", error);
        }
    }

    @Override
    public PageResult<SecurityAuditView> search(
            Connection connection, SecurityAuditQuery query) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(query, "query");
        if (query.page() < 0 || query.pageSize() < 1 || query.pageSize() > 100) {
            throw new IllegalArgumentException("COMMON_VALIDATION_FAILED");
        }
        Filter filter = filter(query);
        long total = count(connection, filter);
        List<SecurityAuditView> items = new ArrayList<>();
        String sql = "SELECT auditId, userId, actionCode, targetType, targetId, "
                + "resultCode, createdAt FROM tblAuditLog" + filter.clause()
                + " ORDER BY createdAt DESC, auditId DESC";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, filter.values());
            try (var rows = statement.executeQuery()) {
                long skipped = 0;
                while (rows.next()) {
                    if (skipped++ < (long) query.page() * query.pageSize()) continue;
                    if (items.size() == query.pageSize()) break;
                    items.add(new SecurityAuditView(rows.getString("auditId"),
                            rows.getString("userId"), rows.getString("actionCode"),
                            rows.getString("targetType"), rows.getString("targetId"),
                            rows.getString("resultCode"),
                            rows.getTimestamp("createdAt").toLocalDateTime()));
                }
            }
            return new PageResult<>(items, query.page(), query.pageSize(), total);
        } catch (SQLException error) {
            throw new PersistenceException("Could not search security audits", error);
        }
    }

    private static Filter filter(SecurityAuditQuery query) {
        List<String> terms = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        if (present(query.userId())) {
            terms.add("(userId=? OR targetId=?)");
            values.add(query.userId().strip());
            values.add(query.userId().strip());
        }
        addText(terms, values, "actionCode", query.actionCode());
        addText(terms, values, "resultCode", query.resultCode());
        if (query.fromInclusive() != null) {
            terms.add("createdAt>=?");
            values.add(Timestamp.valueOf(query.fromInclusive()));
        }
        if (query.toExclusive() != null) {
            terms.add("createdAt<?");
            values.add(Timestamp.valueOf(query.toExclusive()));
        }
        return new Filter(terms.isEmpty() ? "" : " WHERE "
                + String.join(" AND ", terms), values);
    }

    private static void addText(List<String> terms, List<Object> values,
                                String field, String value) {
        if (!present(value)) return;
        terms.add(field + "=?");
        values.add(value.strip());
    }

    private static long count(Connection connection, Filter filter) {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM tblAuditLog" + filter.clause())) {
            bind(statement, filter.values());
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        } catch (SQLException error) {
            throw new PersistenceException("Could not count security audits", error);
        }
    }

    private static void bind(PreparedStatement statement, List<Object> values)
            throws SQLException {
        for (int index = 0; index < values.size(); index++) {
            Object value = values.get(index);
            if (value instanceof Timestamp timestamp) {
                statement.setTimestamp(index + 1, timestamp);
            } else {
                statement.setString(index + 1, (String) value);
            }
        }
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private record Filter(String clause, List<Object> values) { }
}
