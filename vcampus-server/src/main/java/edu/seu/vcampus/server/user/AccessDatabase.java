package edu.seu.vcampus.server.user;

import edu.seu.vcampus.server.persistence.ConnectionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Locale;
import java.util.UUID;

/** Creates the demo Access database and opens connections to it. */
public final class AccessDatabase implements ConnectionProvider {
    private static final Logger DATABASE_LOGGER = LoggerFactory.getLogger("vcampus.database");
    public static final String DEMO_LOGIN_ID = "ADMIN";
    public static final String DEMO_PASSWORD = "Admin1234";

    private final Path path;
    private final String url;

    public AccessDatabase(Path path) {
        this.path = path.toAbsolutePath().normalize();
        this.url = "jdbc:ucanaccess://" + this.path
                + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true";
    }

    /** Creates missing tables and seeds the single demo administrator. */
    public void initialize() throws IOException, SQLException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (Connection connection = open()) {
            if (!hasTable(connection, "tblRole")) {
                createRoleTable(connection);
            }
            if (!hasTable(connection, "tblUser")) {
                createUserTable(connection);
            }
            seedRoles(connection);
            seedDemoUser(connection);
        }
        DATABASE_LOGGER.info("数据库初始化完成 path={}", path);
    }

    @Override
    public Connection open() throws SQLException {
        return DriverManager.getConnection(url);
    }

    private static boolean hasTable(Connection connection, String expected) throws SQLException {
        try (ResultSet tables = connection.getMetaData().getTables(null, null, null,
                new String[] {"TABLE"})) {
            while (tables.next()) {
                if (expected.equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void createRoleTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE tblRole ("
                    + "roleCode VARCHAR(16) PRIMARY KEY, roleName VARCHAR(32) NOT NULL)");
        }
    }

    private static void createUserTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE tblUser ("
                    + "userId VARCHAR(36) PRIMARY KEY, loginId VARCHAR(32) NOT NULL, "
                    + "passwordHash VARCHAR(256) NOT NULL, passwordSalt VARCHAR(128) NOT NULL, "
                    + "passwordIterations LONG NOT NULL, roleCode VARCHAR(16) NOT NULL, "
                    + "accountStatus VARCHAR(16) NOT NULL, mustChangePassword YESNO NOT NULL, "
                    + "failedLoginCount LONG NOT NULL, lockedUntil DATETIME, lastLoginAt DATETIME, "
                    + "rowVersion LONG NOT NULL, createdAt DATETIME NOT NULL, updatedAt DATETIME NOT NULL)");
            statement.executeUpdate("CREATE UNIQUE INDEX uk_tblUser_loginId ON tblUser (loginId)");
        }
    }

    private static void seedRoles(Connection connection) throws SQLException {
        seedRole(connection, "STUDENT", "学生");
        seedRole(connection, "TEACHER", "教师");
        seedRole(connection, "ADMIN", "管理员");
    }

    private static void seedRole(Connection connection, String code, String name) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT roleCode FROM tblRole WHERE roleCode = ?")) {
            query.setString(1, code);
            try (ResultSet result = query.executeQuery()) {
                if (result.next()) {
                    return;
                }
            }
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO tblRole (roleCode, roleName) VALUES (?, ?)")) {
            insert.setString(1, code);
            insert.setString(2, name);
            insert.executeUpdate();
        }
    }

    private static void seedDemoUser(Connection connection) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT userId FROM tblUser WHERE loginId = ?")) {
            query.setString(1, DEMO_LOGIN_ID);
            try (ResultSet result = query.executeQuery()) {
                if (result.next()) {
                    return;
                }
            }
        }

        PasswordHasher.PasswordHash password = new PasswordHasher()
                .hash(DEMO_PASSWORD.toCharArray());
        Timestamp now = new Timestamp(System.currentTimeMillis());
        String sql = "INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, "
                + "passwordIterations, roleCode, accountStatus, mustChangePassword, "
                + "failedLoginCount, lockedUntil, lastLoginAt, rowVersion, createdAt, updatedAt) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement insert = connection.prepareStatement(sql)) {
            insert.setString(1, UUID.randomUUID().toString());
            insert.setString(2, DEMO_LOGIN_ID.toUpperCase(Locale.ROOT));
            insert.setString(3, password.hash());
            insert.setString(4, password.salt());
            insert.setInt(5, password.iterations());
            insert.setString(6, "ADMIN");
            insert.setString(7, "ACTIVE");
            insert.setBoolean(8, false);
            insert.setInt(9, 0);
            insert.setTimestamp(10, null);
            insert.setTimestamp(11, null);
            insert.setInt(12, 0);
            insert.setTimestamp(13, now);
            insert.setTimestamp(14, now);
            insert.executeUpdate();
        }
        DATABASE_LOGGER.info("已创建演示账户 loginId={} role=ADMIN", DEMO_LOGIN_ID);
    }
}
