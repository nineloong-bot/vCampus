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
import java.util.List;

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

    @Test
    void resumesAfterOnlyTheFirstCourseTableWasCreated() throws Exception {
        Path directory = Path.of("target", "test-data");
        Files.createDirectories(directory);
        String url = "jdbc:ucanaccess://" + directory.resolve(UUID.randomUUID() + ".accdb")
                + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true";
        ConnectionProvider database = () -> DriverManager.getConnection(url);
        String firstStatement = Files.readString(schema()).split(";", 2)[0];
        try (Connection connection = database.open()) {
            connection.createStatement().execute(firstStatement);
        }

        new CourseSchemaInitializer(schema()).initialize(database);

        try (Connection connection = database.open()) {
            assertThat(tableNames(connection)).contains("tblterm", "tblcourse", "tblcourseoffering",
                    "tblcourseschedule", "tblenrollment", "tblenrollmentadjustment", "tblcourseattempt");
        }
    }

    @Test
    void installsIdentityForeignKeysWhenUpstreamTablesExist() throws Exception {
        Path directory = Path.of("target", "test-data");
        Files.createDirectories(directory);
        String url = "jdbc:ucanaccess://" + directory.resolve(UUID.randomUUID() + ".accdb")
                + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true";
        ConnectionProvider database = () -> DriverManager.getConnection(url);
        try (Connection connection = database.open()) {
            connection.createStatement().execute("CREATE TABLE tblUser (userId VARCHAR(36) PRIMARY KEY)");
            connection.createStatement().execute("CREATE TABLE tblStudent (studentId VARCHAR(36) PRIMARY KEY)");
        }

        CourseSchemaInitializer initializer = new CourseSchemaInitializer(schema());
        initializer.initialize(database);
        initializer.initialize(database);

        try (Connection connection = database.open()) {
            assertThat(importedKeys(connection, "tblCourseOffering"))
                    .contains("teacherUserId->tblUser.userId");
            assertThat(importedKeys(connection, "tblEnrollment"))
                    .contains("studentId->tblStudent.studentId");
            assertThat(importedKeys(connection, "tblEnrollmentAdjustment"))
                    .contains("studentId->tblStudent.studentId");
            assertThat(importedKeys(connection, "tblCourseAttempt"))
                    .contains("studentId->tblStudent.studentId");
        }
    }

    private static List<String> importedKeys(Connection connection, String table) throws Exception {
        var keys = new java.util.ArrayList<String>();
        try (ResultSet result = connection.getMetaData().getImportedKeys(null, null, table)) {
            while (result.next()) {
                keys.add(result.getString("FKCOLUMN_NAME") + "->" + result.getString("PKTABLE_NAME")
                        + "." + result.getString("PKCOLUMN_NAME"));
            }
        }
        return keys;
    }

    private static Set<String> tableNames(Connection connection) throws Exception {
        Set<String> tables = new HashSet<>();
        try (ResultSet result = connection.getMetaData().getTables(null, null, null, new String[]{"TABLE"})) {
            while (result.next()) tables.add(result.getString("TABLE_NAME").toLowerCase());
        }
        return tables;
    }

    private static Path schema() {
        Path direct = Path.of("vcampus-database", "schema", "030_course.sql");
        return Files.exists(direct) ? direct : Path.of("..", "vcampus-database", "schema", "030_course.sql");
    }
}
