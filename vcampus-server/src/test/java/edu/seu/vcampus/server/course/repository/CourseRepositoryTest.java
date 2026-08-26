package edu.seu.vcampus.server.course.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CourseRepositoryTest {
    private CourseRepository repository;
    private Connection connection;

    @BeforeEach
    void createAccessDatabase() throws Exception {
        Path data = Path.of("target", "test-data");
        Files.createDirectories(data);
        connection = DriverManager.getConnection("jdbc:ucanaccess://" + data.resolve(UUID.randomUUID() + ".accdb")
                + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true");
        for (String statement : Files.readString(schema()).split(";")) {
            if (!statement.isBlank()) connection.createStatement().execute(statement);
        }
        repository = new AccessCourseRepository();
    }

    @Test
    void providesTheCourseSchemaForAccessInitialization() {
        assertThat(Files.exists(schema())).isTrue();
    }

    @Test
    void storesOfferingWithMultipleScheduleRowsVersionAndAuditTimes() {
        seedCatalog();
        Offering saved = repository.insertOffering(connection, offering("offering-1", 30),
                List.of(schedule(DayOfWeek.MONDAY, 1, 2), schedule(DayOfWeek.WEDNESDAY, 3, 4)));

        assertThat(repository.findSchedules(connection, saved.offeringId())).hasSize(2);
        assertThat(saved.rowVersion()).isZero();
        assertThat(saved.createdAt()).isNotNull();
        assertThat(saved.updatedAt()).isEqualTo(saved.createdAt());
    }

    @Test
    void reactivatesDroppedEnrollmentInsteadOfCreatingAnotherNaturalKeyRow() {
        seedCatalog();
        repository.insertOffering(connection, offering("offering-1", 30), List.of(schedule(DayOfWeek.MONDAY, 1, 2)));
        Enrollment original = repository.insertEnrollment(connection, enrollment("enrollment-1", "ACTIVE"));
        Enrollment dropped = repository.updateEnrollment(connection,
                new Enrollment(original.enrollmentId(), original.offeringId(), original.studentId(), "NORMAL", "DROPPED",
                        original.enrolledAt(), Instant.parse("2026-01-12T00:00:00Z"), original.rowVersion(), original.createdAt(), original.updatedAt()),
                original.rowVersion());

        Enrollment reactivated = repository.insertEnrollment(connection, enrollment("new-enrollment-id", "ACTIVE"));

        assertThat(reactivated.enrollmentId()).isEqualTo(original.enrollmentId());
        assertThat(reactivated.enrollmentStatus()).isEqualTo("ACTIVE");
        assertThat(reactivated.droppedAt()).isNull();
        assertThat(reactivated.rowVersion()).isEqualTo(dropped.rowVersion() + 1);
        assertThat(repository.findActiveByStudentAndTerm(connection, "student-1", "term-1"))
                .extracting(Enrollment::enrollmentId).containsExactly(original.enrollmentId());
        assertThat(repository.requireEnrollment(connection, original.enrollmentId()).rowVersion())
                .isEqualTo(reactivated.rowVersion());
    }

    @Test
    void incrementsVersionsWhenMutableRowsChange() {
        seedCatalog();
        Term initialTerm = repository.requireTerm(connection, "term-1");
        Term updatedTerm = repository.updateTerm(connection, initialTerm, initialTerm.rowVersion());
        Offering initialOffering = repository.insertOffering(connection, offering("offering-1", 30), List.of());

        Offering counted = repository.changeEnrolledCount(connection, initialOffering.offeringId(), 1);

        assertThat(updatedTerm.rowVersion()).isEqualTo(1);
        assertThat(updatedTerm.updatedAt()).isAfterOrEqualTo(updatedTerm.createdAt());
        assertThat(counted.enrolledCount()).isEqualTo(1);
        assertThat(counted.rowVersion()).isEqualTo(1);
        assertThat(counted.updatedAt()).isAfterOrEqualTo(counted.createdAt());
    }

    @Test
    void searchesOfferingsByTermKeywordDayAndAvailabilityWithoutServiceSideScanning() throws Exception {
        seedCatalog();
        repository.insertOffering(connection, offering("full-offering", 1),
                List.of(schedule(DayOfWeek.MONDAY, 1, 2)));
        repository.changeEnrolledCount(connection, "full-offering", 1);
        repository.insertOffering(connection,
                new Offering("available-offering", "term-1", "course-1", "teacher-1", "Evening Lab", 30, 0,
                        "OPEN", 0, null, null), List.of(schedule(DayOfWeek.WEDNESDAY, 3, 4)));
        repository.insertOffering(connection,
                new Offering("second-available-offering", "term-1", "course-1", "teacher-1", "Night Lab", 30, 0,
                        "OPEN", 0, null, null), List.of(schedule(DayOfWeek.WEDNESDAY, 5, 6)));

        OfferingSearchPage firstPage = repository.searchOfferings(connection,
                new OfferingSearchCriteria("term-1", "lab", DayOfWeek.WEDNESDAY, true, 0, 1));
        OfferingSearchPage secondPage = repository.searchOfferings(connection,
                new OfferingSearchCriteria("term-1", "lab", DayOfWeek.WEDNESDAY, true, 1, 1));

        assertThat(firstPage.items())
                .extracting(Offering::offeringId).containsExactly("available-offering");
        assertThat(secondPage.items()).extracting(Offering::offeringId).containsExactly("second-available-offering");
        assertThat(firstPage.total()).isEqualTo(2);
    }

    @Test
    void AccessEnforcesCatalogAndEnrollmentUniqueKeys() {
        seedCatalog();
        assertThatThrownBy(() -> repository.insertTerm(connection, new Term("term-2", "2026-2027-1", "Spring",
                LocalDate.of(2027, 2, 1), LocalDate.of(2027, 6, 1), Instant.parse("2027-01-01T00:00:00Z"),
                Instant.parse("2027-01-20T00:00:00Z"), Instant.parse("2027-02-01T00:00:00Z"),
                Instant.parse("2027-02-10T00:00:00Z"), "PLANNED", 0, null, null)))
                .isInstanceOf(edu.seu.vcampus.server.persistence.PersistenceException.class);
        assertThatThrownBy(() -> repository.insertCourse(connection, new Course("course-2", "CS101", "Duplicate",
                java.math.BigDecimal.ONE, 1, null, true, 0, null, null)))
                .isInstanceOf(edu.seu.vcampus.server.persistence.PersistenceException.class);

        repository.insertOffering(connection, offering("offering-1", 30), List.of());
        repository.insertEnrollment(connection, enrollment("enrollment-1", "ACTIVE"));
        assertThatThrownBy(() -> connection.createStatement().executeUpdate("""
                INSERT INTO tblEnrollment (enrollmentId, offeringId, studentId, enrollmentType, enrollmentStatus,
                enrolledAt, rowVersion, createdAt, updatedAt)
                VALUES ('enrollment-2', 'offering-1', 'student-1', 'NORMAL', 'ACTIVE', #2026-01-10#, 0,
                #2026-01-10#, #2026-01-10#)
                """.replaceAll("\\n", " "))).isInstanceOf(SQLException.class);
    }

    @Test
    void AccessEnforcesAllCourseOwnedForeignKeys() {
        seedCatalog();
        repository.insertOffering(connection, offering("offering-1", 30), List.of());

        assertSqlRejected("INSERT INTO tblCourseOffering (offeringId, termId, courseId, teacherUserId, className, capacity, enrolledCount, offeringStatus, rowVersion, createdAt, updatedAt) VALUES ('bad-term', 'missing-term', 'course-1', 'teacher-1', 'bad', 1, 0, 'OPEN', 0, #2026-01-01#, #2026-01-01#)");
        assertSqlRejected("INSERT INTO tblCourseOffering (offeringId, termId, courseId, teacherUserId, className, capacity, enrolledCount, offeringStatus, rowVersion, createdAt, updatedAt) VALUES ('bad-course', 'term-1', 'missing-course', 'teacher-1', 'bad', 1, 0, 'OPEN', 0, #2026-01-01#, #2026-01-01#)");
        assertSqlRejected("INSERT INTO tblCourseSchedule (scheduleId, offeringId, dayOfWeek, startPeriod, endPeriod, startWeek, endWeek, classroom) VALUES ('bad-schedule', 'missing-offering', 1, 1, 1, 1, 1, 'A101')");
        assertSqlRejected("INSERT INTO tblEnrollment (enrollmentId, offeringId, studentId, enrollmentType, enrollmentStatus, enrolledAt, rowVersion, createdAt, updatedAt) VALUES ('bad-enrollment', 'missing-offering', 'student-1', 'NORMAL', 'ACTIVE', #2026-01-01#, 0, #2026-01-01#, #2026-01-01#)");
        assertSqlRejected("INSERT INTO tblCourseAttempt (attemptId, studentId, courseId, termId, outcome, sourceReference, importedAt) VALUES ('bad-attempt-course', 'student-1', 'missing-course', 'term-1', 'FAILED', 'bad-course-source', #2026-01-01#)");
        assertSqlRejected("INSERT INTO tblCourseAttempt (attemptId, studentId, courseId, termId, outcome, sourceReference, importedAt) VALUES ('bad-attempt-term', 'student-1', 'course-1', 'missing-term', 'FAILED', 'bad-term-source', #2026-01-01#)");
    }

    @Test
    void updatesCourseAndOfferingAndRejectsStaleExpectedVersions() {
        seedCatalog();
        Course originalCourse = repository.requireCourse(connection, "course-1");
        Course updatedCourse = repository.updateCourse(connection,
                new Course(originalCourse.courseId(), originalCourse.courseCode(), "Advanced Programming", originalCourse.credit(),
                        64, originalCourse.description(), originalCourse.active(), originalCourse.rowVersion(),
                        originalCourse.createdAt(), originalCourse.updatedAt()), originalCourse.rowVersion());
        assertThat(updatedCourse.courseName()).isEqualTo("Advanced Programming");
        assertThatThrownBy(() -> repository.updateCourse(connection, originalCourse, originalCourse.rowVersion()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Stale course version");

        Offering originalOffering = repository.insertOffering(connection, offering("offering-1", 30),
                List.of(schedule(DayOfWeek.MONDAY, 1, 2)));
        Offering updatedOffering = repository.updateOffering(connection,
                new Offering(originalOffering.offeringId(), originalOffering.termId(), originalOffering.courseId(),
                        originalOffering.teacherUserId(), "Renamed Lab", 35, 0, "CLOSED", originalOffering.rowVersion(),
                        originalOffering.createdAt(), originalOffering.updatedAt()), originalOffering.rowVersion(),
                List.of(schedule(DayOfWeek.FRIDAY, 5, 6)));
        assertThat(updatedOffering.rowVersion()).isEqualTo(1);
        assertThat(repository.findSchedules(connection, updatedOffering.offeringId()))
                .extracting(Schedule::dayOfWeek).containsExactly(DayOfWeek.FRIDAY);
        assertThatThrownBy(() -> repository.updateOffering(connection, originalOffering, originalOffering.rowVersion(), List.of()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Stale offering version");
    }

    @Test
    void persistsAdjustmentAndDeduplicatesAttemptsBySourceReference() {
        seedCatalog();
        EnrollmentAdjustment adjustment = repository.insertAdjustment(connection,
                new EnrollmentAdjustment(null, "student-1", "ADD", null, "offering-1", "SUCCEEDED", null, null));
        CourseAttempt attempt = new CourseAttempt(null, "student-1", "course-1", "term-1", "FAILED", "import-1", null);

        assertThat(adjustment.adjustmentId()).isNotBlank();
        assertThat(repository.insertAttemptIfAbsent(connection, attempt)).isTrue();
        assertThat(repository.insertAttemptIfAbsent(connection, attempt)).isFalse();
        assertThat(repository.existsFailedAttempt(connection, "student-1", "course-1")).isTrue();
        assertThat(repository.findAttempts(connection, "student-1", "course-1")).hasSize(1);
        assertThat(repository.findAdjustmentsByStudent(connection, "student-1"))
                .extracting(EnrollmentAdjustment::adjustmentId).containsExactly(adjustment.adjustmentId());
    }

    private void seedCatalog() {
        repository.insertTerm(connection, new Term("term-1", "2026-2027-1", "Autumn", LocalDate.of(2026, 9, 1),
                LocalDate.of(2027, 1, 15), Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-08-20T00:00:00Z"),
                Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-09-10T00:00:00Z"), "PLANNED", 99, null, null));
        repository.insertCourse(connection, new Course("course-1", "CS101", "Programming", java.math.BigDecimal.valueOf(3.0), 48,
                "intro", true, 99, null, null));
    }

    private static Offering offering(String id, int capacity) {
        return new Offering(id, "term-1", "course-1", "teacher-1", "Class A", capacity, 0, "OPEN", 99, null, null);
    }

    private static Schedule schedule(DayOfWeek day, int start, int end) {
        return new Schedule(null, null, day, start, end, 1, 16, "A101");
    }

    private static Enrollment enrollment(String id, String status) {
        return new Enrollment(id, "offering-1", "student-1", "NORMAL", status,
                Instant.parse("2026-01-10T00:00:00Z"), null, 99, null, null);
    }

    private static Path schema() {
        return Path.of("..", "vcampus-database", "schema", "030_course.sql");
    }

    private void assertSqlRejected(String sql) {
        assertThatThrownBy(() -> connection.createStatement().executeUpdate(sql)).isInstanceOf(SQLException.class);
    }
}
