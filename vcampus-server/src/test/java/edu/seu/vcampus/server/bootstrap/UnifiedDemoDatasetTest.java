package edu.seu.vcampus.server.bootstrap;

import edu.seu.vcampus.server.persistence.ConnectionProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import static org.assertj.core.api.Assertions.assertThat;

class UnifiedDemoDatasetTest {
    @TempDir Path directory;

    @Test
    void freshDatabaseContainsEveryModuleAndManualTestState() throws Exception {
        Path database = directory.resolve("vCampus.accdb");
        ConnectionProvider connections = () -> DriverManager.getConnection(
                "jdbc:ucanaccess://" + database + ";newDatabaseVersion=V2010");
        ApplicationSchemaInitializer initializer = new ApplicationSchemaInitializer(databaseRoot());
        initializer.initialize(connections);
        initializer.initialize(connections);

        try (Connection connection = connections.open()) {
            assertThat(tables(connection)).contains("TBLUSER", "TBLSTUDENT", "TBLCOURSE",
                    "TBLBOOK", "TBLSHOP", "TBLORDER");
            assertThat(count(connection, "SELECT COUNT(*) FROM tblUser WHERE roleCode='ADMIN'"))
                    .isPositive();
            assertThat(count(connection, "SELECT COUNT(*) FROM tblUser WHERE roleCode='TEACHER'"))
                    .isPositive();
            assertThat(count(connection, "SELECT COUNT(*) FROM tblUser WHERE roleCode='STUDENT'"))
                    .isGreaterThanOrEqualTo(4);
            assertThat(count(connection, "SELECT COUNT(*) FROM tblRolePermission WHERE roleCode='ADMIN'"))
                    .isGreaterThanOrEqualTo(7);
            assertThat(count(connection, "SELECT COUNT(*) FROM tblCourseOffering WHERE enrolledCount=capacity"))
                    .isPositive();
            assertThat(count(connection, "SELECT COUNT(*) FROM tblBookLoan WHERE loanStatus='ACTIVE' AND dueAt<NOW()"))
                    .isPositive();
            assertThat(values(connection, "SELECT applicationStatus FROM tblSellerApplication"))
                    .contains("DRAFT", "PENDING", "APPROVED");
            assertThat(values(connection, "SELECT productStatus FROM tblProduct"))
                    .contains("DRAFT", "INACTIVE", "ACTIVE");
            assertThat(count(connection, "SELECT COUNT(*) FROM (SELECT productId FROM tblProductSku GROUP BY productId HAVING COUNT(*)>=2)"))
                    .isPositive();
            assertThat(count(connection, "SELECT COUNT(*) FROM tblCartItem")).isGreaterThanOrEqualTo(2);
            assertThat(values(connection, "SELECT groupStatus FROM tblOrderGroup"))
                    .contains("PENDING_PAYMENT", "PAID");
            for (String login : new String[]{"ADMIN", "TEACHER01", "213230001",
                    "SHOPOWNER", "SHOPDRAFT", "SHOPPENDING"}) {
                assertPassword(connection, login, "admin123");
            }
        }
    }

    private static Set<String> tables(Connection connection) throws Exception {
        Set<String> names = new TreeSet<>();
        try (ResultSet rows = connection.getMetaData().getTables(null, null, null, new String[]{"TABLE"})) {
            while (rows.next()) names.add(rows.getString("TABLE_NAME").toUpperCase());
        }
        return names;
    }

    private static long count(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            rows.next(); return rows.getLong(1);
        }
    }

    private static Set<String> values(Connection connection, String sql) throws Exception {
        Set<String> values = new TreeSet<>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            while (rows.next()) values.add(rows.getString(1));
        }
        return values;
    }

    private static void assertPassword(Connection connection, String login, String password)
            throws Exception {
        try (var statement = connection.prepareStatement(
                "SELECT passwordHash,passwordSalt,passwordIterations FROM tblUser WHERE loginId=?")) {
            statement.setString(1, login);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).as("seeded login %s", login).isTrue();
                PBEKeySpec specification = new PBEKeySpec(password.toCharArray(),
                        Base64.getDecoder().decode(rows.getString(2)), rows.getInt(3), 256);
                byte[] actual = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                        .generateSecret(specification).getEncoded();
                specification.clearPassword();
                assertThat(Base64.getDecoder().decode(rows.getString(1)))
                        .as("password for %s", login).containsExactly(actual);
            }
        }
    }

    private static Path databaseRoot() {
        Path root = Path.of("vcampus-database");
        return (Files.exists(root) ? root : Path.of("..", "vcampus-database"));
    }
}
