package edu.seu.vcampus.server.course.domain;

import edu.seu.vcampus.common.course.AcademicSeason;
import edu.seu.vcampus.server.bootstrap.ApplicationSchemaInitializer;
import edu.seu.vcampus.server.course.repository.*;
import edu.seu.vcampus.server.course.service.StudentEnrollmentEligibility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurriculumSelectionPolicyTest {
    private static final String STUDENT_ID = "00000000-0000-0000-0000-000000000404";
    private Connection connection;
    private CourseRepository courses;
    private CurriculumRepository curricula;
    private Term term;

    @BeforeEach
    void setUp() throws Exception {
        Path data = Path.of("target", "test-data");
        Files.createDirectories(data);
        Path database = data.resolve(UUID.randomUUID() + ".accdb");
        String url = "jdbc:ucanaccess://" + database
                + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true";
        new ApplicationSchemaInitializer(databaseRoot()).initialize(
                () -> DriverManager.getConnection(url));
        connection = DriverManager.getConnection(url);
        courses = new AccessCourseRepository();
        curricula = new AccessCurriculumRepository();
        seedAcademicOrganization();
        Instant now = Instant.parse("2026-09-01T00:00:00Z");
        term = courses.insertTerm(connection, new Term(null, "2026-AUTUMN", "2026-2027 秋季",
                LocalDate.of(2026, 9, 1), LocalDate.of(2027, 1, 15), 2026, AcademicSeason.AUTUMN,
                now, now.plusSeconds(3600), now, now.plusSeconds(7200), "ACTIVE", 0, null, null));
        seed();
    }

    @AfterEach void close() throws Exception { connection.close(); }

    @Test
    void mapsCohortToCurrentTermAndAddsOnlyUnresolvedEarlierFailures() {
        var student = new StudentEnrollmentEligibility(STUDENT_ID, "ACTIVE", "080901", 2024);
        var candidates = new CurriculumSelectionPolicy(curricula, courses)
                .resolve(connection, student, term);

        assertThat(candidates.courses().keySet()).containsExactlyInAnyOrder("current", "retake");
        assertThat(candidates.isRetake("retake")).isTrue();
        assertThat(candidates.allows("future")).isFalse();
        assertThat(candidates.allows("passed-current")).isFalse();
    }

    @Test
    void reportsMissingPublishedPlanInsteadOfShowingEveryOffering() {
        assertThatThrownBy(() -> new CurriculumSelectionPolicy(curricula, courses).resolve(
                connection, new StudentEnrollmentEligibility(STUDENT_ID, "ACTIVE", "080901", 2025), term))
                .isInstanceOf(CurriculumNotConfiguredException.class)
                .hasMessage("尚未配置适用的培养方案");
    }

    private void seed() {
        addCourse("current", "B09D0012", "数据库原理");
        addCourse("future", "B09S0061", "软件工程");
        addCourse("retake", "BJSL0061", "数据结构");
        addCourse("passed-current", "B09T0011", "计算机组成原理");
        curricula.insertPlan(connection, new CurriculumPlan(
                "plan", "080901", 2024, "2024级计算机科学与技术", 1, "PUBLISHED"));
        addPlanCourse("current", 3, AcademicSeason.AUTUMN);
        addPlanCourse("passed-current", 3, AcademicSeason.AUTUMN);
        addPlanCourse("future", 3, AcademicSeason.SPRING);
        addPlanCourse("retake", 2, AcademicSeason.SPRING);
        courses.insertAttemptIfAbsent(connection, new CourseAttempt("failed", STUDENT_ID, "retake",
                term.termId(), "FAILED", "grade-failed", Instant.parse("2026-01-01T00:00:00Z")));
        courses.insertAttemptIfAbsent(connection, new CourseAttempt("passed", STUDENT_ID, "passed-current",
                term.termId(), "PASSED", "grade-passed", Instant.parse("2026-01-01T00:00:00Z")));
    }

    private void addCourse(String id, String code, String name) {
        courses.insertCourse(connection, new Course(id, code, name, new BigDecimal("3.0"),
                48, null, true, 0, null, null));
    }

    private void addPlanCourse(String courseId, int year, AcademicSeason season) {
        curricula.insertCourse(connection, new CurriculumCourse("pc-" + courseId, "plan", courseId,
                year, season, "REQUIRED", "专业主干课", "计算机科学与工程学院"));
    }

    private void seedAcademicOrganization() throws Exception {
        connection.createStatement().execute("INSERT INTO tblDepartment "
                + "(departmentId,departmentCode,departmentName,isActive,rowVersion) "
                + "VALUES ('dept','TEST-CS','计算机科学与工程学院',TRUE,0)");
        connection.createStatement().execute("INSERT INTO tblMajor "
                + "(majorId,departmentId,majorCode,majorName,isActive,rowVersion) "
                + "VALUES ('major','dept','080901','计算机科学与技术',TRUE,0)");
    }

    private static Path databaseRoot() {
        Path direct = Path.of("vcampus-database");
        return Files.exists(direct) ? direct : Path.of("..", "vcampus-database");
    }
}
