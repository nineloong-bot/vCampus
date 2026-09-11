package edu.seu.vcampus.server.course.repository;

import edu.seu.vcampus.common.course.AcademicSeason;
import edu.seu.vcampus.server.bootstrap.ApplicationSchemaInitializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CurriculumRepositoryTest {
    private Connection connection;
    private CurriculumRepository repository;

    @BeforeEach
    void createDatabase() throws Exception {
        Path data = Path.of("target", "test-data");
        Files.createDirectories(data);
        Path database = data.resolve(UUID.randomUUID() + ".accdb");
        String url = "jdbc:ucanaccess://" + database
                + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true";
        new ApplicationSchemaInitializer(databaseRoot()).initialize(
                () -> DriverManager.getConnection(url));
        connection = DriverManager.getConnection(url);
        repository = new AccessCurriculumRepository();
        seedAcademicOrganization();
        seedCourses();
    }

    @AfterEach void closeDatabase() throws Exception { connection.close(); }

    @Test
    void selectsPublishedPlanByMajorAndCohort() throws Exception {
        repository.insertPlan(connection, new CurriculumPlan(
                "plan-2024-cs", "080901", 2024, "2024级计算机科学与技术", 1, "PUBLISHED"));
        repository.insertPlan(connection, new CurriculumPlan(
                "plan-draft", "080901", 2025, "2025草稿", 1, "DRAFT"));

        assertThat(count("SELECT COUNT(*) FROM tblTrainingPlan WHERE planId IN ('plan-2024-cs','plan-draft')"))
                .isEqualTo(2);
        assertThat(repository.findPublishedPlan(connection, "080901", 2024))
                .get().extracting(CurriculumPlan::planId).isEqualTo("plan-2024-cs");
        assertThat(repository.findPublishedPlan(connection, "080901", 2025)).isEmpty();
    }

    @Test
    void partitionsCurrentAndEarlierCoursesAndLoadsPrerequisites() {
        repository.insertPlan(connection, new CurriculumPlan(
                "plan", "080901", 2024, "2024级计算机科学与技术", 1, "PUBLISHED"));
        repository.insertCourse(connection, curriculum("pc-data", "course-data", 2,
                AcademicSeason.SPRING, "REQUIRED", "大类学科基础课"));
        repository.insertCourse(connection, curriculum("pc-db", "course-db", 3,
                AcademicSeason.AUTUMN, "REQUIRED", "专业主干课"));
        repository.insertCourse(connection, curriculum("pc-software", "course-software", 3,
                AcademicSeason.SPRING, "REQUIRED", "专业主干课"));
        repository.insertPrerequisite(connection, "edge", "plan", "course-db", "course-data");

        assertThat(repository.findScheduledCourses(connection, "plan", 3, AcademicSeason.AUTUMN))
                .extracting(CurriculumCourse::courseId).containsExactly("course-db");
        assertThat(repository.findEarlierCourses(connection, "plan", 3, AcademicSeason.AUTUMN))
                .extracting(CurriculumCourse::courseId).containsExactly("course-data");
        assertThat(repository.findPrerequisiteCourseIds(connection, "plan", "course-db"))
                .containsExactly("course-data");
    }

    @Test
    void prerequisiteGraphMustRemainAcyclic() {
        assertThat(CurriculumGraphValidator.validate(Map.of(
                "course-db", Set.of("course-data"),
                "course-software", Set.of("course-db"))))
                .isEqualTo(List.of("course-data", "course-db", "course-software"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> CurriculumGraphValidator.validate(Map.of(
                        "course-data", Set.of("course-software"),
                        "course-db", Set.of("course-data"),
                        "course-software", Set.of("course-db"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cycle");
    }

    private CurriculumCourse curriculum(String id, String courseId, int year,
                                        AcademicSeason season, String nature, String category) {
        return new CurriculumCourse(id, "plan", courseId, year, season, nature, category,
                "计算机科学与工程学院");
    }

    private void seedCourses() throws Exception {
        insertCourse("course-data", "BJSL0061", "数据结构", "4.0");
        insertCourse("course-db", "B09D0012", "数据库原理", "3.0");
        insertCourse("course-software", "B09S0061", "软件工程", "3.0");
    }

    private void seedAcademicOrganization() throws Exception {
        connection.createStatement().execute("INSERT INTO tblDepartment "
                + "(departmentId,departmentCode,departmentName,isActive,rowVersion) "
                + "VALUES ('dept','TEST-CS','计算机科学与工程学院',TRUE,0)");
        connection.createStatement().execute("INSERT INTO tblMajor "
                + "(majorId,departmentId,majorCode,majorName,isActive,rowVersion) "
                + "VALUES ('major','dept','080901','计算机科学与技术',TRUE,0)");
    }

    private long count(String sql) throws Exception {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            rows.next(); return rows.getLong(1);
        }
    }

    private void insertCourse(String id, String code, String name, String credit) throws Exception {
        try (var statement = connection.prepareStatement("""
                INSERT INTO tblCourse (courseId, courseCode, courseName, credit, totalHours,
                  description, isActive, rowVersion, createdAt, updatedAt)
                VALUES (?, ?, ?, ?, 48, NULL, TRUE, 0, NOW(), NOW())
                """)) {
            statement.setString(1, id); statement.setString(2, code); statement.setString(3, name);
            statement.setBigDecimal(4, new BigDecimal(credit)); statement.executeUpdate();
        }
    }

    private static Path databaseRoot() {
        Path direct = Path.of("vcampus-database");
        return Files.exists(direct) ? direct : Path.of("..", "vcampus-database");
    }
}
