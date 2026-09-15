package edu.seu.vcampus.server.course.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.seu.vcampus.common.course.AdminEnrollStudentCommand;
import edu.seu.vcampus.common.course.CourseOutcome;
import edu.seu.vcampus.common.course.CreateCourseCommand;
import edu.seu.vcampus.common.course.CreateOfferingCommand;
import edu.seu.vcampus.common.course.CreateTermCommand;
import edu.seu.vcampus.common.course.EnrollmentView;
import edu.seu.vcampus.common.course.ImportCourseOutcomesCommand;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.course.domain.ScheduleConflictPolicy;
import edu.seu.vcampus.server.course.domain.TermWindowPolicy;
import edu.seu.vcampus.server.course.repository.AccessCourseRepository;
import edu.seu.vcampus.server.course.repository.CourseRepository;
import edu.seu.vcampus.server.course.repository.Enrollment;
import edu.seu.vcampus.server.persistence.ConnectionProvider;
import edu.seu.vcampus.server.persistence.TransactionManager;

class AdminEnrollmentServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-14T08:00:00Z");
    private CourseService service;
    private CourseRepository repository;
    private TransactionManager transactions;
    private String offeringId;

    @BeforeEach
    void setUp() throws Exception {
        Path data = Path.of("target", "test-data");
        Files.createDirectories(data);
        String url = "jdbc:ucanaccess://" + data.resolve(UUID.randomUUID() + ".accdb")
                + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true";
        try (Connection connection = DriverManager.getConnection(url)) {
            for (String sql : Files.readString(Path.of("..", "vcampus-database", "schema", "030_course.sql")).split(";")) {
                if (!sql.isBlank()) connection.createStatement().execute(sql);
            }
        }
        ConnectionProvider connections = () -> DriverManager.getConnection(url);
        transactions = new TransactionManager(connections);
        repository = new AccessCourseRepository();
        CourseStudentGateway students = CourseStudentGateway.of(
                userId -> new StudentEnrollmentEligibility("student-1", "ACTIVE"),
                studentId -> true,
                studentNumber -> "213260001".equals(studentNumber)
                        ? new StudentEnrollmentEligibility("student-1", "ACTIVE") : null);
        CourseAuthorizationGateway authorization = new CourseAuthorizationGateway() {
            @Override public CourseSessionIdentity requireSession(String token) {
                return new CourseSessionIdentity("admin-1", "ADMIN");
            }
            @Override public void requireUserRole(String userId, String role) { }
        };
        service = new CourseServiceImpl(authorization, students, repository,
                new StripedResourceLockManager(), transactions, new TermWindowPolicy(),
                new ScheduleConflictPolicy(), Clock.fixed(NOW, ZoneOffset.UTC));
        var term = service.createTerm(new CreateTermCommand("2026-A", "秋季学期",
                LocalDate.of(2026, 9, 1), LocalDate.of(2027, 1, 15),
                NOW.minusSeconds(3600), NOW.plusSeconds(3600), NOW.plusSeconds(7200),
                NOW.plusSeconds(10800), "ACTIVE"));
        var course = service.createCourse(new CreateCourseCommand(
                "CS101", "程序设计", BigDecimal.valueOf(3), 48, "课程简介", true));
        offeringId = service.createOffering(new CreateOfferingCommand(term.termId(), course.courseId(),
                "teacher-1", "程序设计-A班", 40, 5, "OPEN", List.of())).offeringId();
        service.importCourseOutcomes(new ImportCourseOutcomesCommand(List.of(
                new ImportCourseOutcomesCommand.OutcomeEntry(
                        "student-1", course.courseId(), term.termId(), CourseOutcome.FAILED, "grade-1"))));
        fillRetakeQuota();
    }

    @Test
    void administratorCanPlaceEligibleRetakeBeyondSelfServiceQuota() {
        EnrollmentView result = service.adminEnrollStudent(
                new AdminEnrollStudentCommand("213260001", offeringId));

        assertThat(result.enrollmentType()).isEqualTo("RETAKE");
        transactions.inTransaction(connection -> {
            assertThat(repository.requireOffering(connection, offeringId).enrolledCount()).isZero();
            assertThat(repository.findRetakeQuota(connection, offeringId))
                    .satisfies(quota -> {
                        assertThat(quota.capacity()).isEqualTo(6);
                        assertThat(quota.enrolledCount()).isEqualTo(6);
                    });
            assertThat(repository.findAdjustmentsByStudent(connection, "student-1"))
                    .extracting(row -> row.adjustmentType()).contains("ADMIN_RETAKE_ADD");
            return null;
        });
    }

    @Test
    void administratorCanPlaceOrdinaryStudentWithoutFailedAttempt() {
        transactions.inTransaction(connection -> {
            repository.findAttempts(connection, "student-1",
                    repository.requireOffering(connection, offeringId).courseId()).forEach(attempt -> {
                        try (var statement = connection.prepareStatement(
                                "DELETE FROM tblCourseAttempt WHERE attemptId=?")) {
                            statement.setString(1, attempt.attemptId());
                            statement.executeUpdate();
                        } catch (java.sql.SQLException error) {
                            throw new IllegalStateException(error);
                        }
                    });
            return null;
        });
        fillNormalCapacity();
        transactions.inTransaction(connection -> {
            assertThat(repository.requireOffering(connection, offeringId).enrolledCount()).isEqualTo(40);
            return null;
        });

        EnrollmentView result = service.adminEnrollStudent(
                new AdminEnrollStudentCommand("213260001", offeringId));

        assertThat(result.enrollmentType()).isEqualTo("NORMAL");
        transactions.inTransaction(connection -> {
            assertThat(repository.requireOffering(connection, offeringId))
                    .satisfies(offering -> {
                        assertThat(offering.capacity()).isEqualTo(41);
                        assertThat(offering.enrolledCount()).isEqualTo(41);
                    });
            assertThat(repository.findAdjustmentsByStudent(connection, "student-1"))
                    .extracting(row -> row.adjustmentType()).contains("ADMIN_ADD");
            return null;
        });
    }

    private void fillNormalCapacity() {
        transactions.inTransaction(connection -> {
            for (int index = 1; index <= 40; index++) {
                repository.insertEnrollment(connection, new Enrollment(null, offeringId,
                        "ordinary-" + index, "NORMAL", "ACTIVE", NOW, null, 0, null, null));
            }
            try (var statement = connection.prepareStatement(
                    "UPDATE tblCourseOffering SET enrolledCount=40 WHERE offeringId=?")) {
                statement.setString(1, offeringId);
                statement.executeUpdate();
            } catch (java.sql.SQLException error) {
                throw new IllegalStateException(error);
            }
            return null;
        });
    }

    private void fillRetakeQuota() {
        transactions.inTransaction(connection -> {
            repository.saveRetakeCapacity(connection, offeringId, 5);
            for (int index = 1; index <= 5; index++) {
                repository.insertEnrollment(connection, new Enrollment(null, offeringId,
                        "existing-" + index, "RETAKE", "ACTIVE", NOW, null, 0, null, null));
                repository.changeEnrolledCount(connection, offeringId, "RETAKE", 1);
            }
            return null;
        });
    }
}
