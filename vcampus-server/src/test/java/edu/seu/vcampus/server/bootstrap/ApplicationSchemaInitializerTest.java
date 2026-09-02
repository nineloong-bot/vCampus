package edu.seu.vcampus.server.bootstrap;

import edu.seu.vcampus.server.persistence.ConnectionProvider;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationSchemaInitializerTest {
    @Test
    void installsCommonUserSeedsAndCourseSchemaIdempotently() throws Exception {
        Path database = Files.createTempDirectory("vcampus-schema-").resolve("schema.accdb");
        ConnectionProvider connections = () -> DriverManager.getConnection(
                "jdbc:ucanaccess://" + database + ";newDatabaseVersion=V2010");
        ApplicationSchemaInitializer initializer = new ApplicationSchemaInitializer(databaseRoot());

        initializer.initialize(connections);
        initializer.initialize(connections);

        try (Connection connection = connections.open()) {
            assertThat(tableNames(connection)).contains("tblrequestdedup", "tblrole", "tbluser",
                    "tblauditlog", "tblterm", "tblcourse", "tblcourseoffering", "tblenrollment");
            assertThat(count(connection, "SELECT COUNT(*) FROM tblRole")).isEqualTo(3);
            assertThat(count(connection, "SELECT COUNT(*) FROM tblPermission")).isEqualTo(4);
            assertThat(count(connection, "SELECT COUNT(*) FROM tblRolePermission")).isEqualTo(4);
            assertThat(count(connection,
                    "SELECT COUNT(*) FROM tblUser WHERE loginId = 'ADMIN'")).isEqualTo(1);
        }
    }

    private static Set<String> tableNames(Connection connection) throws Exception {
        Set<String> names = new HashSet<>();
        try (ResultSet result = connection.getMetaData().getTables(null, null, null, new String[]{"TABLE"})) {
            while (result.next()) names.add(result.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
        }
        return names;
    }

    private static int count(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        }
    }

    private static Path databaseRoot() {
        Path root = Path.of("vcampus-database");
        return Files.exists(root) ? root : Path.of("..", "vcampus-database");
    }
}
