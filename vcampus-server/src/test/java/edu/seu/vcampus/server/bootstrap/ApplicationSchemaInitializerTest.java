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
    void upgradesExistingStudentSchemaWithLatestMajorFields() throws Exception {
        Path database = Files.createTempDirectory("vcampus-schema-upgrade-").resolve("schema.accdb");
        ConnectionProvider connections = () -> DriverManager.getConnection(
                "jdbc:ucanaccess://" + database + ";newDatabaseVersion=V2010");
        try (Connection connection = connections.open(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE tblMajor (majorId VARCHAR(36) PRIMARY KEY, "
                    + "departmentId VARCHAR(36) NOT NULL, majorCode VARCHAR(3) NOT NULL, "
                    + "majorName VARCHAR(64) NOT NULL, isActive BOOLEAN NOT NULL, "
                    + "rowVersion LONG NOT NULL)");
        }

        new ApplicationSchemaInitializer(databaseRoot()).initialize(connections);

        try (Connection connection = connections.open()) {
            assertThat(columnNames(connection, "tblMajor")).contains("grades");
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT grades FROM tblMajor")) {
                assertThat(result.next()).isTrue();
            }
        }
    }

    @Test
    void installsAllModuleSchemasAndPermissionSeedsIdempotently() throws Exception {
        Path database = Files.createTempDirectory("vcampus-schema-").resolve("schema.accdb");
        ConnectionProvider connections = () -> DriverManager.getConnection(
                "jdbc:ucanaccess://" + database + ";newDatabaseVersion=V2010");
        ApplicationSchemaInitializer initializer = new ApplicationSchemaInitializer(databaseRoot());

        initializer.initialize(connections);
        initializer.initialize(connections);

        try (Connection connection = connections.open()) {
            assertThat(tableNames(connection)).contains("tblrequestdedup", "tblrole", "tbluser",
                    "tblauditlog", "tblstudent", "tblterm", "tblcourse", "tblcourseoffering",
                    "tblenrollment", "tblbook", "tblshop", "tblorder",
                    "tblstudentcollegeadministrator", "tblmajortransferapplication",
                    "tbltrainingplan", "tblstudentgrade");
            assertThat(count(connection, "SELECT COUNT(*) FROM tblRole")).isEqualTo(10);
            assertThat(count(connection, "SELECT COUNT(*) FROM tblPermission")).isEqualTo(14);
            assertThat(count(connection, "SELECT COUNT(*) FROM tblRolePermission")).isEqualTo(15);
            assertThat(count(connection,
                    "SELECT COUNT(*) FROM tblUser WHERE loginId = 'ADMIN'")).isEqualTo(1);
            assertThat(uniqueIndexColumns(connection, "tblTrainingPlan"))
                    .contains(java.util.List.of("majorid", "enrollmentyear"));
            assertThat(uniqueIndexColumns(connection, "tblTrainingPlanCourse"))
                    .contains(java.util.List.of("planid", "coursecode"));
            assertThat(uniqueIndexColumns(connection, "tblStudentGrade"))
                    .contains(java.util.List.of("studentid", "plancourseid"));
        }

        try (Connection connection = connections.open(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE tblStudent SET counselorName='用户修改' "
                    + "WHERE studentId='00000000-0000-0000-0000-000000000210'");
            statement.executeUpdate("UPDATE tblNumberSequence SET currentValue=900 "
                    + "WHERE sequenceKey='CAMPUS_CARD_GLOBAL'");
        }
        initializer.initialize(connections);
        try (Connection connection = connections.open(); Statement statement = connection.createStatement()) {
            try (ResultSet result = statement.executeQuery("SELECT counselorName FROM tblStudent "
                    + "WHERE studentId='00000000-0000-0000-0000-000000000210'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("用户修改");
            }
            try (ResultSet result = statement.executeQuery("SELECT currentValue FROM tblNumberSequence "
                    + "WHERE sequenceKey='CAMPUS_CARD_GLOBAL'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getLong(1)).isEqualTo(900);
            }
        }
    }

    private static Set<String> tableNames(Connection connection) throws Exception {
        Set<String> names = new HashSet<>();
        try (ResultSet result = connection.getMetaData().getTables(null, null, null, new String[]{"TABLE"})) {
            while (result.next()) names.add(result.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
        }
        return names;
    }

    private static Set<String> columnNames(Connection connection, String table) throws Exception {
        Set<String> names = new HashSet<>();
        try (ResultSet result = connection.getMetaData().getColumns(null, null, table, null)) {
            while (result.next()) names.add(result.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
        }
        return names;
    }

    private static java.util.List<java.util.List<String>> uniqueIndexColumns(
            Connection connection, String table) throws Exception {
        java.util.Map<String, java.util.List<String>> indexes = new java.util.LinkedHashMap<>();
        try (ResultSet result = connection.getMetaData().getIndexInfo(null, null, table, true, false)) {
            while (result.next()) {
                String index = result.getString("INDEX_NAME");
                String column = result.getString("COLUMN_NAME");
                if (index != null && column != null) {
                    indexes.computeIfAbsent(index, ignored -> new java.util.ArrayList<>())
                            .add(column.toLowerCase(Locale.ROOT));
                }
            }
        }
        return new java.util.ArrayList<>(indexes.values());
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
