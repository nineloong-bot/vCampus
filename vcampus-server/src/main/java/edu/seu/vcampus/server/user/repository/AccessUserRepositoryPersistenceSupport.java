package edu.seu.vcampus.server.user.repository;

import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.user.UserSearchQuery;
import edu.seu.vcampus.server.persistence.PersistenceException;
import edu.seu.vcampus.server.user.domain.UserAccount;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Holds mapping and SQL support for {@link AccessUserRepository}. */
abstract class AccessUserRepositoryPersistenceSupport implements UserRepository {
    protected static final String COLUMNS = "userId, loginId, passwordHash, passwordSalt, "
            + "passwordIterations, roleCode, accountStatus, mustChangePassword, "
            + "failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt";
    protected static final String INSERT_SQL = "INSERT INTO tblUser (" + COLUMNS
            + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    protected static final String UPDATE_SQL = """
            UPDATE tblUser SET loginId=?, passwordHash=?, passwordSalt=?,
                passwordIterations=?, roleCode=?, accountStatus=?,
                mustChangePassword=?, failedLoginCount=?, lockedUntil=?,
                lastLoginAt=?, rowVersion=rowVersion+1, updatedAt=?
            WHERE userId=? AND rowVersion=?
            """;

    protected Optional<UserAccount> findOne(Connection connection, String field, String value) {
        String sql = "SELECT " + COLUMNS + " FROM tblUser WHERE " + field + "=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        ? Optional.of(UserAccountRowMapper.map(result)) : Optional.empty();
            }
        } catch (SQLException error) {
            throw failure("Could not read user account", error);
        }
    }

    protected static Filter filter(UserSearchQuery query) {
        List<String> conditions = new ArrayList<>();
        List<String> parameters = new ArrayList<>();
        if (query.keyword() != null && !query.keyword().isBlank()) {
            conditions.add("loginId LIKE ?");
            parameters.add("%" + normalize(query.keyword()) + "%");
        }
        if (query.role() != null) {
            conditions.add("roleCode=?");
            parameters.add(query.role().name());
        }
        if (query.status() != null) {
            conditions.add("accountStatus=?");
            parameters.add(query.status().name());
        }
        return new Filter(conditions.isEmpty() ? "" : " WHERE "
                + String.join(" AND ", conditions), parameters);
    }

    protected static long count(Connection connection, Filter filter) {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM tblUser" + filter.clause())) {
            bind(statement, filter.parameters());
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        } catch (SQLException error) {
            throw failure("Could not count user accounts", error);
        }
    }

    protected static void bind(PreparedStatement statement, List<String> values)
            throws SQLException {
        for (int index = 0; index < values.size(); index++) {
            statement.setString(index + 1, values.get(index));
        }
    }

    protected static String normalize(String loginId) {
        return Objects.requireNonNull(loginId, "loginId").strip().toUpperCase(Locale.ROOT);
    }

    protected static PersistenceException failure(String message, SQLException cause) {
        return new PersistenceException(message, cause);
    }

    protected record Filter(String clause, List<String> parameters) {
    }
}
