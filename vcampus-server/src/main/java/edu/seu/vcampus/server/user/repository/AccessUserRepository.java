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

/** Access JDBC implementation of transactional user account persistence. */
public final class AccessUserRepository implements UserRepository {
    private static final String COLUMNS = "userId, loginId, passwordHash, passwordSalt, "
            + "passwordIterations, roleCode, accountStatus, mustChangePassword, "
            + "failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt";
    private static final String INSERT_SQL = "INSERT INTO tblUser (" + COLUMNS
            + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String UPDATE_SQL = """
            UPDATE tblUser SET loginId=?, passwordHash=?, passwordSalt=?,
                passwordIterations=?, roleCode=?, accountStatus=?,
                mustChangePassword=?, failedLoginCount=?, lockedUntil=?,
                lastLoginAt=?, rowVersion=rowVersion+1, updatedAt=?
            WHERE userId=? AND rowVersion=?
            """;

    @Override
    public Optional<UserAccount> findById(Connection connection, String userId) {
        return findOne(connection, "userId", Objects.requireNonNull(userId, "userId"));
    }

    @Override
    public Optional<UserAccount> findByNormalizedLoginId(
            Connection connection, String normalizedLoginId) {
        return findOne(connection, "loginId", normalize(normalizedLoginId));
    }

    @Override
    public void insert(Connection connection, UserAccount account) {
        Objects.requireNonNull(account, "account");
        try (PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
            statement.setString(1, account.userId());
            statement.setString(2, normalize(account.loginId()));
            statement.setString(3, account.passwordHash());
            statement.setString(4, account.passwordSalt());
            statement.setInt(5, account.passwordIterations());
            statement.setString(6, account.role().name());
            statement.setString(7, account.accountStatus().name());
            statement.setBoolean(8, account.mustChangePassword());
            statement.setInt(9, account.failedLoginCount());
            UserAccountRowMapper.setTimestamp(statement, 10, account.lockedUntil());
            UserAccountRowMapper.setTimestamp(statement, 11, account.lastLoginAt());
            statement.setLong(12, account.rowVersion());
            UserAccountRowMapper.setTimestamp(statement, 13, account.createdAt());
            UserAccountRowMapper.setTimestamp(statement, 14, account.updatedAt());
            statement.executeUpdate();
        } catch (SQLException error) {
            if (UserConstraintClassifier.isDuplicateLoginId(
                    connection, normalize(account.loginId()), error)) {
                throw new DuplicateLoginIdException(error);
            }
            throw failure("Could not insert user account", error);
        }
    }

    @Override
    public void updateWithVersion(
            Connection connection, UserAccount account, long expectedVersion) {
        try (PreparedStatement statement = connection.prepareStatement(UPDATE_SQL)) {
            statement.setString(1, normalize(account.loginId()));
            statement.setString(2, account.passwordHash());
            statement.setString(3, account.passwordSalt());
            statement.setInt(4, account.passwordIterations());
            statement.setString(5, account.role().name());
            statement.setString(6, account.accountStatus().name());
            statement.setBoolean(7, account.mustChangePassword());
            statement.setInt(8, account.failedLoginCount());
            UserAccountRowMapper.setTimestamp(statement, 9, account.lockedUntil());
            UserAccountRowMapper.setTimestamp(statement, 10, account.lastLoginAt());
            UserAccountRowMapper.setTimestamp(statement, 11, account.updatedAt());
            statement.setString(12, account.userId());
            statement.setLong(13, expectedVersion);
            if (statement.executeUpdate() != 1) {
                throw new ConcurrentModificationException("User account version is stale");
            }
        } catch (SQLException error) {
            throw failure("Could not update user account", error);
        }
    }

    @Override
    public PageResult<UserAccount> search(Connection connection, UserSearchQuery query) {
        Filter filter = filter(query);
        long total = count(connection, filter);
        List<UserAccount> pageItems = new ArrayList<>();
        String sql = "SELECT " + COLUMNS + " FROM tblUser" + filter.clause()
                + " ORDER BY loginId";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, filter.parameters());
            try (ResultSet result = statement.executeQuery()) {
                long skipped = 0;
                while (result.next()) {
                    if (skipped++ < (long) query.page() * query.pageSize()) {
                        continue;
                    }
                    if (pageItems.size() == query.pageSize()) {
                        break;
                    }
                    pageItems.add(UserAccountRowMapper.map(result));
                }
            }
            return new PageResult<>(pageItems, query.page(), query.pageSize(), total);
        } catch (SQLException error) {
            throw failure("Could not search user accounts", error);
        }
    }

    private Optional<UserAccount> findOne(Connection connection, String field, String value) {
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

    private static Filter filter(UserSearchQuery query) {
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

    private static long count(Connection connection, Filter filter) {
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

    private static void bind(PreparedStatement statement, List<String> values)
            throws SQLException {
        for (int index = 0; index < values.size(); index++) {
            statement.setString(index + 1, values.get(index));
        }
    }

    private static String normalize(String loginId) {
        return Objects.requireNonNull(loginId, "loginId").strip().toUpperCase(Locale.ROOT);
    }

    private static PersistenceException failure(String message, SQLException cause) {
        return new PersistenceException(message, cause);
    }

    private record Filter(String clause, List<String> parameters) {
    }
}
