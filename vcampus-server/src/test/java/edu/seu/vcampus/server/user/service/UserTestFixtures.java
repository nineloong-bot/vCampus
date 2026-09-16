package edu.seu.vcampus.server.user.service;

import java.sql.Connection;

/** Small explicit user fixtures for tests that exercise audit and administration flows. */
public final class UserTestFixtures {
    /** Stable administrator identifier used by isolated user-service tests. */
    public static final String ADMIN_ID = "00000000-0000-0000-0000-000000000001";

    private UserTestFixtures() {
    }

    /** Inserts the administrator required by an isolated user-service test database. */
    public static void insertSuperAdministrator(Connection connection) throws Exception {
        try (var statement = connection.prepareStatement("""
                INSERT INTO tblUser
                    (userId,loginId,passwordHash,passwordSalt,passwordIterations,roleCode,
                     accountStatus,mustChangePassword,failedLoginCount,rowVersion,createdAt,updatedAt)
                VALUES (?, 'ADMIN_FIXTURE', ?, ?, 120000, 'SUPER_ADMIN', 'ACTIVE', FALSE, 0, 0, NOW(), NOW())
                """)) {
            statement.setString(1, ADMIN_ID);
            statement.setString(2, "qX+wANpmojiY0I1qjpBBoUCjiFP6bZJnWg5qgeHmNh4=");
            statement.setString(3, "mW5pbqIFUpGT2Zlkq7TsSA==");
            statement.executeUpdate();
        }
    }
}
