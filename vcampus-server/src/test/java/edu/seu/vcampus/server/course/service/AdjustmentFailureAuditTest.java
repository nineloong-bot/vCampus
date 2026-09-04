package edu.seu.vcampus.server.course.service;

import edu.seu.vcampus.common.course.DropCommand;
import edu.seu.vcampus.common.course.ChangeOfferingCommand;
import edu.seu.vcampus.common.course.LateAddCommand;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.course.domain.CourseForbiddenException;
import edu.seu.vcampus.server.course.domain.StudentIneligibleException;
import edu.seu.vcampus.server.course.domain.OfferingFullException;
import edu.seu.vcampus.server.course.domain.ScheduleConflictPolicy;
import edu.seu.vcampus.server.course.domain.TermWindowPolicy;
import edu.seu.vcampus.server.course.repository.AccessCourseRepository;
import edu.seu.vcampus.server.course.repository.Course;
import edu.seu.vcampus.server.course.repository.CourseRepository;
import edu.seu.vcampus.server.course.repository.Enrollment;
import edu.seu.vcampus.server.course.repository.EnrollmentAdjustment;
import edu.seu.vcampus.server.course.repository.Offering;
import edu.seu.vcampus.server.course.repository.Term;
import edu.seu.vcampus.server.persistence.ConnectionProvider;
import edu.seu.vcampus.server.persistence.TransactionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class AdjustmentFailureAuditTest {
    private static final Instant NOW = Instant.parse("2026-08-10T00:00:00Z");
    private CourseRepository repository;
    private ConnectionProvider connections;

    @BeforeEach
    void setUp() throws Exception {
        Path data = Path.of("target", "test-data");
        Files.createDirectories(data);
        String url = "jdbc:ucanaccess://" + data.resolve(UUID.randomUUID() + ".accdb")
                + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true";
        try (Connection connection = DriverManager.getConnection(url)) {
            for (String statement : Files.readString(schema()).split(";")) {
                if (!statement.isBlank()) connection.createStatement().execute(statement);
            }
        }
        connections = () -> DriverManager.getConnection(url);
        repository = new AccessCourseRepository();
    }

    @Test
    void lockedRoleDriftWritesFailureAuditWithoutMutatingTheOffering() {
        seedCatalogAndOffering("target", 2, 0);
        AtomicInteger calls = new AtomicInteger();
        CourseService service = service(ignored -> calls.incrementAndGet() == 1
                ? new CourseSessionIdentity("user", "STUDENT")
                : new CourseSessionIdentity("user", "TEACHER"), repository);

        assertThatThrownBy(() -> service.addDuringAdjustment("token", new LateAddCommand("target")))
                .isInstanceOf(CourseForbiddenException.class);

        assertThat(activeCount("target")).isZero();
        assertThat(offering("target").enrolledCount()).isZero();
        assertThat(adjustments()).singleElement().satisfies(a -> {
            assertThat(a.operationResult()).isEqualTo("FAILED");
            assertThat(a.failureCode()).isEqualTo("COMMON_FORBIDDEN");
        });
    }

    @Test
    void lockedUserAndEligibilityDriftWriteFailureAuditsWithoutMutatingTheOffering() {
        seedCatalogAndOffering("target", 2, 0);
        AtomicInteger userCalls = new AtomicInteger();
        CourseService userDrift = new CourseServiceImpl(ignored -> userCalls.incrementAndGet() == 1
                ? new CourseSessionIdentity("user", "STUDENT") : new CourseSessionIdentity("other-user", "STUDENT"),
                ignored -> new StudentEnrollmentEligibility("student", "ACTIVE"), repository,
                new StripedResourceLockManager(), new TransactionManager(connections), new TermWindowPolicy(),
                new ScheduleConflictPolicy(), Clock.fixed(NOW, ZoneOffset.UTC));
        assertThatThrownBy(() -> userDrift.addDuringAdjustment("token", new LateAddCommand("target")))
                .isInstanceOf(CourseForbiddenException.class);

        AtomicInteger statusCalls = new AtomicInteger();
        CourseService statusDrift = new CourseServiceImpl(ignored -> new CourseSessionIdentity("user", "STUDENT"),
                ignored -> statusCalls.incrementAndGet() == 1
                        ? new StudentEnrollmentEligibility("student", "ACTIVE")
                        : new StudentEnrollmentEligibility("student", "SUSPENDED"), repository,
                new StripedResourceLockManager(), new TransactionManager(connections), new TermWindowPolicy(),
                new ScheduleConflictPolicy(), Clock.fixed(NOW, ZoneOffset.UTC));
        assertThatThrownBy(() -> statusDrift.addDuringAdjustment("token", new LateAddCommand("target")))
                .hasMessageContaining("COURSE_STUDENT_INELIGIBLE");

        assertThat(activeCount("target")).isZero();
        assertThat(adjustments()).hasSize(2).extracting(EnrollmentAdjustment::operationResult).containsOnly("FAILED");
    }

    @Test
    void lockedStudentIdDriftRejectsLateAddDropAndChangeAndWritesFailureAudits() {
        seedCatalogAndOffering("add-target", 2, 0);
        seedSecondOffering("drop-source", "course-2", 2, 1);
        seedSecondOffering("change-source", "course-3", 2, 1);
        seedSecondOffering("change-target", "course-4", 2, 0);
        Enrollment dropSource = seedActive("drop-source", "student");
        Enrollment changeSource = seedActive("change-source", "student");

        assertThatThrownBy(() -> studentIdDriftService().addDuringAdjustment("token", new LateAddCommand("add-target")))
                .isInstanceOf(StudentIneligibleException.class);
        assertThatThrownBy(() -> studentIdDriftService().dropDuringAdjustment("token", new DropCommand(dropSource.enrollmentId(), 0)))
                .isInstanceOf(StudentIneligibleException.class);
        assertThatThrownBy(() -> studentIdDriftService().changeDuringAdjustment("token",
                new ChangeOfferingCommand(changeSource.enrollmentId(), "change-target", 0)))
                .isInstanceOf(StudentIneligibleException.class);

        assertThat(activeCount("add-target")).isZero();
        assertThat(enrollment(dropSource.enrollmentId()).enrollmentStatus()).isEqualTo("ACTIVE");
        assertThat(enrollment(changeSource.enrollmentId()).enrollmentStatus()).isEqualTo("ACTIVE");
        assertThat(activeCount("change-target")).isZero();
        assertThat(offering("add-target").enrolledCount()).isZero();
        assertThat(offering("drop-source").enrolledCount()).isEqualTo(1);
        assertThat(offering("change-source").enrolledCount()).isEqualTo(1);
        assertThat(offering("change-target").enrolledCount()).isZero();
        assertThat(adjustments()).hasSize(3).extracting(EnrollmentAdjustment::adjustmentType,
                EnrollmentAdjustment::operationResult, EnrollmentAdjustment::failureCode)
                .containsExactlyInAnyOrder(
                        tuple("ADD", "FAILED", "COURSE_STUDENT_INELIGIBLE"),
                        tuple("DROP", "FAILED", "COURSE_STUDENT_INELIGIBLE"),
                        tuple("CHANGE", "FAILED", "COURSE_STUDENT_INELIGIBLE"));
    }

    @Test
    void missingAndForeignSourcesAreIndistinguishableAndBothWriteFailureAudits() {
        seedCatalogAndOffering("source", 2, 1);
        Enrollment foreign = seedActive("source", "other-student");
        CourseService service = service(ignored -> new CourseSessionIdentity("user", "STUDENT"), repository);

        assertThatThrownBy(() -> service.dropDuringAdjustment("token", new DropCommand("missing", 0)))
                .isInstanceOf(CourseForbiddenException.class)
                .extracting("code").isEqualTo("COMMON_FORBIDDEN");
        assertThatThrownBy(() -> service.dropDuringAdjustment("token", new DropCommand(foreign.enrollmentId(), 0)))
                .isInstanceOf(CourseForbiddenException.class)
                .extracting("code").isEqualTo("COMMON_FORBIDDEN");

        assertThat(enrollment(foreign.enrollmentId()).enrollmentStatus()).isEqualTo("ACTIVE");
        assertThat(offering("source").enrolledCount()).isEqualTo(1);
        assertThat(adjustments()).hasSize(2).allSatisfy(a -> {
            assertThat(a.operationResult()).isEqualTo("FAILED");
            assertThat(a.failureCode()).isEqualTo("COMMON_FORBIDDEN");
        });
        assertThat(adjustments()).extracting(EnrollmentAdjustment::sourceOfferingId).containsOnlyNulls();
    }

    @Test
    void missingAndForeignChangeSourcesAreIndistinguishableAndBothWriteFailureAudits() {
        seedCatalogAndOffering("source", 2, 1);
        seedSecondOffering("target", "course-2", 2, 0);
        Enrollment foreign = seedActive("source", "other-student");
        CourseService service = service(ignored -> new CourseSessionIdentity("user", "STUDENT"), repository);

        assertThatThrownBy(() -> service.changeDuringAdjustment("token", new ChangeOfferingCommand("missing", "target", 0)))
                .isInstanceOf(CourseForbiddenException.class).extracting("code").isEqualTo("COMMON_FORBIDDEN");
        assertThatThrownBy(() -> service.changeDuringAdjustment("token",
                new ChangeOfferingCommand(foreign.enrollmentId(), "target", 0)))
                .isInstanceOf(CourseForbiddenException.class).extracting("code").isEqualTo("COMMON_FORBIDDEN");

        assertThat(enrollment(foreign.enrollmentId()).enrollmentStatus()).isEqualTo("ACTIVE");
        assertThat(activeCount("target")).isZero();
        assertThat(adjustments()).hasSize(2).allSatisfy(a -> {
            assertThat(a.operationResult()).isEqualTo("FAILED");
            assertThat(a.failureCode()).isEqualTo("COMMON_FORBIDDEN");
            assertThat(a.sourceOfferingId()).isNull();
        });
    }

    @Test
    void failedAuditWritePreservesTheBusinessExceptionAndSuppressesInfrastructureFault() {
        seedCatalogAndOffering("full", 1, 1);
        seedActive("full", "other-student");
        CourseService service = service(ignored -> new CourseSessionIdentity("user", "STUDENT"),
                failFailedAuditInsert(repository));

        assertThatThrownBy(() -> service.addDuringAdjustment("token", new LateAddCommand("full")))
                .isInstanceOf(OfferingFullException.class)
                .satisfies(failure -> assertThat(failure.getSuppressed())
                        .extracting(Throwable::getMessage).contains("forced failure-audit write"));

        assertThat(activeCount("full")).isEqualTo(1);
        assertThat(offering("full").enrolledCount()).isEqualTo(1);
    }

    private CourseService service(CourseAuthorizationGateway authorization, CourseRepository courseRepository) {
        return new CourseServiceImpl(authorization,
                ignored -> new StudentEnrollmentEligibility("student", "ACTIVE"), courseRepository,
                new StripedResourceLockManager(), new TransactionManager(connections), new TermWindowPolicy(),
                new ScheduleConflictPolicy(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void seedCatalogAndOffering(String offeringId, int capacity, int count) {
        inTransaction(c -> {
            repository.insertTerm(c, new Term("term", "2026-1", "Term", LocalDate.of(2026, 9, 1),
                    LocalDate.of(2027, 1, 1), NOW.minusSeconds(3600), NOW.minusSeconds(1800),
                    NOW.minusSeconds(60), NOW.plusSeconds(60), "ACTIVE", 0, null, null));
            repository.insertSelectionPhase(c, new edu.seu.vcampus.server.course.repository.SelectionPhase(
                    "phase", "term", "ADJUSTMENT", "Adjustment selection", "OPEN", 0, null, null));
            repository.insertCourse(c, new Course("course", "CS101", "Course", BigDecimal.ONE, 16,
                    null, true, 0, null, null));
            repository.insertOffering(c, new Offering(offeringId, "term", "course", "teacher", offeringId,
                    capacity, count, "OPEN", 0, null, null), List.of());
            return null;
        });
    }

    private Enrollment seedActive(String offeringId, String studentId) {
        return inTransaction(c -> repository.insertEnrollment(c, new Enrollment(UUID.randomUUID().toString(), offeringId,
                studentId, "NORMAL", "ACTIVE", NOW.minusSeconds(1), null, 0, null, null)));
    }

    private void seedSecondOffering(String offeringId, String courseId, int capacity, int count) {
        inTransaction(c -> {
            repository.insertCourse(c, new Course(courseId, courseId, courseId, BigDecimal.ONE, 16,
                    null, true, 0, null, null));
            return repository.insertOffering(c, new Offering(offeringId, "term", courseId, "teacher", offeringId,
                    capacity, count, "OPEN", 0, null, null), List.of());
        });
    }

    private CourseService studentIdDriftService() {
        AtomicInteger calls = new AtomicInteger();
        return new CourseServiceImpl(ignored -> new CourseSessionIdentity("user", "STUDENT"),
                ignored -> calls.incrementAndGet() == 1
                        ? new StudentEnrollmentEligibility("student", "ACTIVE")
                        : new StudentEnrollmentEligibility("other-student", "ACTIVE"), repository,
                new StripedResourceLockManager(), new TransactionManager(connections), new TermWindowPolicy(),
                new ScheduleConflictPolicy(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private CourseRepository failFailedAuditInsert(CourseRepository delegate) {
        return (CourseRepository) Proxy.newProxyInstance(CourseRepository.class.getClassLoader(),
                new Class<?>[]{CourseRepository.class}, (proxy, method, args) -> {
                    if ("insertAdjustment".equals(method.getName())
                            && "FAILED".equals(((EnrollmentAdjustment) args[1]).operationResult())) {
                        throw new IllegalStateException("forced failure-audit write");
                    }
                    try { return method.invoke(delegate, args); }
                    catch (InvocationTargetException error) { throw error.getCause(); }
                });
    }

    private Enrollment enrollment(String id) { return inTransaction(c -> repository.requireEnrollment(c, id)); }
    private Offering offering(String id) { return inTransaction(c -> repository.requireOffering(c, id)); }
    private List<EnrollmentAdjustment> adjustments() { return inTransaction(c -> repository.findAdjustmentsByStudent(c, "student")); }
    private long activeCount(String offeringId) {
        try (Connection c = connections.open(); var s = c.prepareStatement(
                "SELECT COUNT(*) FROM tblEnrollment WHERE offeringId=? AND enrollmentStatus='ACTIVE'")) {
            s.setString(1, offeringId); try (var r = s.executeQuery()) { r.next(); return r.getLong(1); }
        } catch (Exception error) { throw new IllegalStateException(error); }
    }
    private <T> T inTransaction(edu.seu.vcampus.server.persistence.SqlWork<T> work) {
        return new TransactionManager(connections).inTransaction(work);
    }
    private static Path schema() { return Path.of("..", "vcampus-database", "schema", "030_course.sql"); }
}
