package edu.seu.vcampus.server.course.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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
}
