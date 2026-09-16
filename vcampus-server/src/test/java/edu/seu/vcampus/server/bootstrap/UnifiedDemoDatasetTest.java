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

import static org.assertj.core.api.Assertions.assertThat;

class UnifiedDemoDatasetTest {
    @TempDir Path directory;

    @Test
    void freshDatabaseContainsEveryModuleButNoBusinessDataset() throws Exception {
        Path database = directory.resolve("vCampus.accdb");
        ConnectionProvider connections = () -> DriverManager.getConnection(
                "jdbc:ucanaccess://" + database + ";newDatabaseVersion=V2010");
        ApplicationSchemaInitializer initializer = new ApplicationSchemaInitializer(databaseRoot());
        initializer.initialize(connections);
        initializer.initialize(connections);

        try (Connection connection = connections.open()) {
            assertThat(tables(connection))
                    .contains("TBLUSER", "TBLSTUDENT", "TBLCOURSE", "TBLTRAININGPLAN",
                            "TBLTRAININGPLANCOURSE", "TBLBOOK", "TBLSHOP", "TBLORDER")
                    .doesNotContain("TBLCURRICULUMPLAN", "TBLCURRICULUMCOURSE",
                            "TBLCURRICULUMPREREQUISITE");
            assertThat(count(connection, "SELECT COUNT(*) FROM tblUser")).isZero();
            assertThat(count(connection, "SELECT COUNT(*) FROM tblStudent")).isZero();
            assertThat(count(connection, "SELECT COUNT(*) FROM tblRolePermission WHERE roleCode='SUPER_ADMIN'"))
                    .isGreaterThanOrEqualTo(3);
            assertThat(count(connection, "SELECT COUNT(*) FROM tblTrainingPlan")).isZero();
            assertThat(count(connection, "SELECT COUNT(*) FROM tblBookLoan")).isZero();
            assertThat(values(connection, "SELECT applicationStatus FROM tblSellerApplication")).isEmpty();
            assertThat(values(connection, "SELECT productStatus FROM tblProduct")).isEmpty();
            assertThat(count(connection, "SELECT COUNT(*) FROM tblCartItem")).isZero();
            assertThat(values(connection, "SELECT groupStatus FROM tblOrderGroup")).isEmpty();
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

    private static Path databaseRoot() {
        Path root = Path.of("vcampus-database");
        return (Files.exists(root) ? root : Path.of("..", "vcampus-database"));
    }
}
