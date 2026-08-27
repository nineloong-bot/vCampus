package edu.seu.vcampus.server.user.repository;

import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.server.user.domain.UserAccount;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/** Package-local JDBC conversion helpers for persisted user accounts. */
final class UserAccountRowMapper {
    private UserAccountRowMapper() {
    }

    static UserAccount map(ResultSet result) throws SQLException {
        return new UserAccount(result.getString("userId"), result.getString("loginId"),
                result.getString("passwordHash"), result.getString("passwordSalt"),
                result.getInt("passwordIterations"),
                UserRole.valueOf(result.getString("roleCode")),
                AccountStatus.valueOf(result.getString("accountStatus")),
                result.getBoolean("mustChangePassword"), result.getInt("failedLoginCount"),
                localDateTime(result, "lockedUntil"), localDateTime(result, "lastLoginAt"),
                result.getLong("rowVersion"), localDateTime(result, "createdAt"),
                localDateTime(result, "updatedAt"));
    }

    static void setTimestamp(PreparedStatement statement, int index,
                             LocalDateTime value) throws SQLException {
        statement.setTimestamp(index, value == null ? null : Timestamp.valueOf(value));
    }

    private static LocalDateTime localDateTime(ResultSet result, String column)
            throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }
}
