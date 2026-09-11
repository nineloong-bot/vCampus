package edu.seu.vcampus.server.bootstrap;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SeededStudentDatasetTest {
    @Test void releaseSeedContainsSearchableStudentsAcrossClassesAndStatuses() throws Exception {
        Path database = Path.of("target", "test-data", UUID.randomUUID() + ".accdb");
        Files.createDirectories(database.getParent());
        DatabaseInitializer.main(new String[] {
                projectDirectory("schema").toString(), projectDirectory("seed").toString(), database.toString()
        });

        try (var connection = DriverManager.getConnection("jdbc:ucanaccess://" + database
                + ";immediatelyReleaseResources=true")) {
            assertThat(count(connection, "SELECT COUNT(*) FROM tblStudent")).isGreaterThanOrEqualTo(100);
            assertThat(count(connection, "SELECT COUNT(DISTINCT classId) FROM tblStudent")).isGreaterThanOrEqualTo(3);
            assertThat(count(connection, "SELECT COUNT(DISTINCT studentStatus) FROM tblStudent")).isGreaterThanOrEqualTo(4);
            assertThat(count(connection, "SELECT COUNT(*) FROM tblUser WHERE roleCode='STUDENT'"))
                    .isGreaterThanOrEqualTo(100);
            assertThat(count(connection, "SELECT currentValue FROM tblNumberSequence WHERE sequenceKey='CAMPUS_CARD_GLOBAL'"))
                    .isGreaterThanOrEqualTo(100);
            assertThat(count(connection, "SELECT COUNT(*) FROM tblDepartment")).isGreaterThanOrEqualTo(3);
            assertThat(count(connection, "SELECT COUNT(*) FROM tblMajorTransferBatch")).isGreaterThanOrEqualTo(2);
            assertThat(count(connection, "SELECT COUNT(*) FROM tblMajorTransferOption")).isGreaterThanOrEqualTo(3);
            assertThat(count(connection, "SELECT COUNT(*) FROM tblTrainingPlan")).isGreaterThanOrEqualTo(1);
            assertThat(count(connection, "SELECT COUNT(*) FROM tblTrainingPlanCourse")).isGreaterThanOrEqualTo(4);
            assertThat(primaryKeyColumns(connection, "tblTerm")).containsExactly("termid");
            assertThat(primaryKeyColumns(connection, "tblEnrollment")).containsExactly("enrollmentid");
            assertThat(primaryKeyColumns(connection, "tblStudentCollegeAdministrator"))
                    .containsExactlyInAnyOrder("departmentid", "userid");
            assertThat(uniqueIndexColumns(connection, "tblTrainingPlan"))
                    .contains(List.of("majorid", "enrollmentyear"));
            assertThat(uniqueIndexColumns(connection, "tblTrainingPlanCourse"))
                    .contains(List.of("planid", "coursecode"));
            assertThat(uniqueIndexColumns(connection, "tblStudentGrade"))
                    .contains(List.of("studentid", "plancourseid"));
        }
    }

    private static List<List<String>> uniqueIndexColumns(java.sql.Connection connection,
                                                          String table) throws Exception {
        java.util.Map<String, List<String>> indexes = new java.util.LinkedHashMap<>();
        try (var result = connection.getMetaData().getIndexInfo(null, null, table, true, false)) {
            while (result.next()) {
                String index = result.getString("INDEX_NAME");
                String column = result.getString("COLUMN_NAME");
                if (index != null && column != null) {
                    indexes.computeIfAbsent(index, ignored -> new ArrayList<>())
                            .add(column.toLowerCase(Locale.ROOT));
                }
            }
        }
        return new ArrayList<>(indexes.values());
    }

    private static List<String> primaryKeyColumns(java.sql.Connection connection, String table)
            throws Exception {
        List<String> columns = new ArrayList<>();
        try (var result = connection.getMetaData().getPrimaryKeys(null, null, table)) {
            while (result.next()) {
                columns.add(result.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
            }
        }
        return columns;
    }

    private static long count(java.sql.Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            result.next(); return result.getLong(1);
        }
    }

    private static Path projectDirectory(String child) {
        Path current = Path.of("").toAbsolutePath();
        Path databaseModule = current.getFileName().toString().equals("vcampus-server")
                ? current.resolve("..").resolve("vcampus-database") : current.resolve("vcampus-database");
        return databaseModule.resolve(child).normalize();
    }
}
