package edu.seu.vcampus.server.course.composition;

import edu.seu.vcampus.server.persistence.ConnectionProvider;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CourseSchemaInitializerTest {
    @Test
    void createsAllCourseTablesAndCanRunAgain() throws Exception {
        Path directory = Path.of("target", "test-data");
        Files.createDirectories(directory);
        String url = "jdbc:ucanaccess://" + directory.resolve(UUID.randomUUID() + ".accdb")
                + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true";
        ConnectionProvider database = () -> DriverManager.getConnection(url);
        CourseSchemaInitializer initializer = new CourseSchemaInitializer(schema());

        initializer.initialize(database);
        initializer.initialize(database);

        try (Connection connection = database.open()) {
            Set<String> tables = new HashSet<>();
            try (ResultSet result = connection.getMetaData().getTables(null, null, null, new String[]{"TABLE"})) {
                while (result.next()) tables.add(result.getString("TABLE_NAME").toLowerCase());
            }
            assertThat(tables).contains("tblterm", "tblcourse", "tblcourseoffering", "tblcourseschedule",
                    "tblenrollment", "tblenrollmentadjustment", "tblcourseattempt");
        }
    }

    private static Path schema() {
        Path direct = Path.of("vcampus-database", "schema", "030_course.sql");
        return Files.exists(direct) ? direct : Path.of("..", "vcampus-database", "schema", "030_course.sql");
    }
}
