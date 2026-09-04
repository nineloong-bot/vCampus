package edu.seu.vcampus.server.course.service;

import edu.seu.vcampus.common.course.ChangeOfferingCommand;
import edu.seu.vcampus.common.course.DropCommand;
import edu.seu.vcampus.common.course.LateAddCommand;
import edu.seu.vcampus.common.course.EnrollmentView;
import edu.seu.vcampus.server.concurrency.ResourceKey;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.course.domain.CourseForbiddenException;
import edu.seu.vcampus.server.course.domain.ChangeTargetInvalidException;
import edu.seu.vcampus.server.course.domain.DropClosedException;
import edu.seu.vcampus.server.course.domain.EnrollmentNotActiveException;
import edu.seu.vcampus.server.course.domain.OfferingFullException;
import edu.seu.vcampus.server.course.domain.ScheduleConflictPolicy;
import edu.seu.vcampus.server.course.domain.TermWindowPolicy;
import edu.seu.vcampus.server.course.repository.AccessCourseRepository;
import edu.seu.vcampus.server.course.repository.Course;
import edu.seu.vcampus.server.course.repository.CourseRepository;
import edu.seu.vcampus.server.course.repository.Enrollment;
import edu.seu.vcampus.server.course.repository.EnrollmentAdjustment;
import edu.seu.vcampus.server.course.repository.Offering;
import edu.seu.vcampus.server.course.repository.Schedule;
import edu.seu.vcampus.server.course.repository.Term;
import edu.seu.vcampus.server.course.repository.SelectionPhase;
import edu.seu.vcampus.server.persistence.ConnectionProvider;
import edu.seu.vcampus.server.persistence.TransactionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnrollmentAdjustmentTest {
    private static final Instant NOW = Instant.parse("2026-08-10T00:00:00Z");
    private static final String TOKEN = "student-token";
    private static final String USER = "user-1";
    private static final String STUDENT = "student-1";

    private final Map<String, CourseSessionIdentity> sessions = new ConcurrentHashMap<>();
    private final Map<String, StudentEnrollmentEligibility> students = new ConcurrentHashMap<>();
    private CourseRepository repository;
    private ConnectionProvider connections;
    private CourseService service;

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
        sessions.put(TOKEN, new CourseSessionIdentity(USER, "STUDENT"));
        students.put(USER, new StudentEnrollmentEligibility(STUDENT, "ACTIVE"));
        service = new CourseServiceImpl(sessions::get, students::get, repository,
                new StripedResourceLockManager(), new TransactionManager(connections),
                new TermWindowPolicy(), new ScheduleConflictPolicy(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void lateAddCreatesActiveLateAddAndWritesSuccessAudit() {
        seedCatalog();
        seedOffering("target", "course-1", 2, 0);

        EnrollmentView result = service.addDuringAdjustment(TOKEN, new LateAddCommand("target"));

        assertThat(result.enrollmentType()).isEqualTo("LATE_ADD");
        assertThat(result.enrollmentStatus()).isEqualTo("ACTIVE");
        assertThat(activeCount("target")).isEqualTo(1);
        assertThat(offering("target").enrolledCount()).isEqualTo(1);
        assertThat(adjustments()).singleElement().satisfies(a -> {
            assertThat(a.adjustmentType()).isEqualTo("ADD");
            assertThat(a.targetOfferingId()).isEqualTo("target");
            assertThat(a.operationResult()).isEqualTo("SUCCEEDED");
            assertThat(a.failureCode()).isNull();
        });
    }

    @Test
    void dropChecksOwnerVersionAndActiveStateThenPreservesDroppedHistory() {
        seedCatalog();
        seedOffering("source", "course-1", 2, 1);
        Enrollment source = seedActive("source", STUDENT);

        service.dropDuringAdjustment(TOKEN, new DropCommand(source.enrollmentId(), source.rowVersion()));

        Enrollment dropped = enrollment(source.enrollmentId());
        assertThat(dropped.enrollmentStatus()).isEqualTo("DROPPED");
        assertThat(dropped.droppedAt()).isEqualTo(NOW);
        assertThat(dropped.rowVersion()).isEqualTo(source.rowVersion() + 1);
        assertThat(offering("source").enrolledCount()).isZero();
        assertThat(adjustments()).singleElement().satisfies(a -> {
            assertThat(a.adjustmentType()).isEqualTo("DROP");
            assertThat(a.sourceOfferingId()).isEqualTo("source");
            assertThat(a.operationResult()).isEqualTo("SUCCEEDED");
        });

        assertThatThrownBy(() -> service.dropDuringAdjustment(TOKEN,
                new DropCommand(source.enrollmentId(), source.rowVersion() + 1)))
                .isInstanceOf(EnrollmentNotActiveException.class)
                .extracting("code").isEqualTo("COURSE_ENROLLMENT_NOT_ACTIVE");
        assertThat(offering("source").enrolledCount()).isZero();
    }

    @Test
    void dropDuringNormalEnrollmentWindowPersistsRowCountVersionAndSuccessAudit() {
        seedCatalog(NOW.minusSeconds(60), NOW.plusSeconds(60),
                NOW.plusSeconds(120), NOW.plusSeconds(180));
        seedOffering("source", "course-1", 2, 1);
        Enrollment source = seedActive("source", STUDENT);

        service.drop(TOKEN, new DropCommand(source.enrollmentId(), source.rowVersion()));

        Enrollment dropped = enrollment(source.enrollmentId());
        assertThat(dropped.enrollmentStatus()).isEqualTo("DROPPED");
        assertThat(dropped.droppedAt()).isEqualTo(NOW);
        assertThat(dropped.rowVersion()).isEqualTo(source.rowVersion() + 1);
        assertThat(offering("source").enrolledCount()).isZero();
        assertThat(adjustments()).singleElement().satisfies(a -> {
            assertThat(a.adjustmentType()).isEqualTo("DROP");
            assertThat(a.sourceOfferingId()).isEqualTo("source");
            assertThat(a.operationResult()).isEqualTo("SUCCEEDED");
            assertThat(a.failureCode()).isNull();
        });
    }

    @Test
    void dropDuringGapLeavesEnrollmentVersionAndOfferingCountUnchanged() {
        seedCatalog(NOW.minusSeconds(120), NOW.minusSeconds(60),
                NOW.plusSeconds(60), NOW.plusSeconds(120));
        seedOffering("source", "course-1", 2, 1);
        Enrollment source = seedActive("source", STUDENT);

        assertThatThrownBy(() -> service.drop(TOKEN,
                new DropCommand(source.enrollmentId(), source.rowVersion())))
                .isInstanceOf(DropClosedException.class)
                .extracting("code").isEqualTo("COURSE_DROP_NOT_OPEN");

        Enrollment unchanged = enrollment(source.enrollmentId());
        assertThat(unchanged.enrollmentStatus()).isEqualTo("ACTIVE");
        assertThat(unchanged.droppedAt()).isNull();
        assertThat(unchanged.rowVersion()).isEqualTo(source.rowVersion());
        assertThat(offering("source").enrolledCount()).isEqualTo(1);
        assertThat(adjustments()).singleElement().satisfies(a -> {
            assertThat(a.adjustmentType()).isEqualTo("DROP");
            assertThat(a.operationResult()).isEqualTo("FAILED");
            assertThat(a.failureCode()).isEqualTo("COURSE_DROP_NOT_OPEN");
        });
    }

    @Test
    void rejectsAnotherStudentsDropWithoutChangingTheirEnrollment() {
        seedCatalog();
        seedOffering("source", "course-1", 2, 1);
        Enrollment source = seedActive("source", "other-student");

        assertThatThrownBy(() -> service.dropDuringAdjustment(TOKEN,
                new DropCommand(source.enrollmentId(), source.rowVersion())))
                .isInstanceOf(CourseForbiddenException.class);

        assertThat(enrollment(source.enrollmentId()).enrollmentStatus()).isEqualTo("ACTIVE");
        assertThat(offering("source").enrolledCount()).isEqualTo(1);
        assertThat(adjustments()).singleElement().satisfies(a -> {
            assertThat(a.operationResult()).isEqualTo("FAILED");
            assertThat(a.failureCode()).isEqualTo("COMMON_FORBIDDEN");
        });
    }

    @Test
    void failedChangeKeepsSourceAndWritesFailureAudit() {
        seedCatalog();
        seedOffering("source", "course-1", 2, 1);
        seedCourse("course-2", "CS102");
        seedOffering("target", "course-2", 1, 1);
        Enrollment source = seedActive("source", STUDENT);
        seedActive("target", "other-student");

        assertThatThrownBy(() -> service.changeDuringAdjustment(TOKEN,
                new ChangeOfferingCommand(source.enrollmentId(), "target", source.rowVersion())))
                .isInstanceOf(OfferingFullException.class);

        assertThat(enrollment(source.enrollmentId()).enrollmentStatus()).isEqualTo("ACTIVE");
        assertThat(activeCount("target")).isEqualTo(1);
        assertThat(offering("source").enrolledCount()).isEqualTo(1);
        assertThat(offering("target").enrolledCount()).isEqualTo(1);
        assertThat(adjustments()).singleElement().satisfies(a -> {
            assertThat(a.adjustmentType()).isEqualTo("CHANGE");
            assertThat(a.operationResult()).isEqualTo("FAILED");
            assertThat(a.failureCode()).isEqualTo("COURSE_OFFERING_FULL");
        });
    }

    @Test
    void changeAtomicallyMovesEnrollmentAndAuditsSuccess() {
        seedCatalog();
        seedOffering("source", "course-1", 2, 1);
        seedCourse("course-2", "CS102");
        seedOffering("target", "course-2", 2, 0);
        Enrollment source = seedActive("source", STUDENT);

        EnrollmentView target = service.changeDuringAdjustment(TOKEN,
                new ChangeOfferingCommand(source.enrollmentId(), "target", source.rowVersion()));

        assertThat(target.offeringId()).isEqualTo("target");
        assertThat(target.enrollmentStatus()).isEqualTo("ACTIVE");
        assertThat(enrollment(source.enrollmentId()).enrollmentStatus()).isEqualTo("DROPPED");
        assertThat(offering("source").enrolledCount()).isZero();
        assertThat(offering("target").enrolledCount()).isEqualTo(1);
        assertThat(adjustments()).singleElement().satisfies(a -> {
            assertThat(a.adjustmentType()).isEqualTo("CHANGE");
            assertThat(a.operationResult()).isEqualTo("SUCCEEDED");
            assertThat(a.sourceOfferingId()).isEqualTo("source");
            assertThat(a.targetOfferingId()).isEqualTo("target");
        });
    }

    @Test
    void changeLocksStudentThenDistinctOfferingsInLexicalOrder() {
        seedCatalog();
        seedOffering("zulu", "course-1", 2, 1);
        seedCourse("course-2", "CS102");
        seedOffering("alpha", "course-2", 2, 0);
        Enrollment source = seedActive("zulu", STUDENT);
        List<ResourceKey> acquired = new ArrayList<>();
        ResourceLockManager recording = new ResourceLockManager() {
            @Override
            public <T> T withLocks(List<ResourceKey> keys, Supplier<T> action) {
                acquired.addAll(keys);
                return action.get();
            }
        };
        CourseService lockedService = new CourseServiceImpl(sessions::get, students::get, repository, recording,
                new TransactionManager(connections), new TermWindowPolicy(), new ScheduleConflictPolicy(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        lockedService.changeDuringAdjustment(TOKEN,
                new ChangeOfferingCommand(source.enrollmentId(), "alpha", source.rowVersion()));

        assertThat(acquired).containsExactly(new ResourceKey("STUDENT", STUDENT),
                new ResourceKey("OFFERING", "alpha"), new ResourceKey("OFFERING", "zulu"));
    }

    @Test
    void targetWriteFailureRollsBackBothEnrollmentAndOfferingCounts() {
        seedCatalog();
        seedOffering("source", "course-1", 2, 1);
        seedCourse("course-2", "CS102");
        seedOffering("target", "course-2", 2, 0);
        Enrollment source = seedActive("source", STUDENT);
        CourseRepository failingRepository = failTargetCountChange("target");
        CourseService failingService = new CourseServiceImpl(sessions::get, students::get, failingRepository,
                new StripedResourceLockManager(), new TransactionManager(connections), new TermWindowPolicy(),
                new ScheduleConflictPolicy(), Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> failingService.changeDuringAdjustment(TOKEN,
                new ChangeOfferingCommand(source.enrollmentId(), "target", source.rowVersion())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("forced target offering-count failure");

        assertThat(enrollment(source.enrollmentId()).enrollmentStatus()).isEqualTo("ACTIVE");
        assertThat(activeCount("target")).isZero();
        assertThat(offering("source").enrolledCount()).isEqualTo(1);
        assertThat(offering("target").enrolledCount()).isZero();
    }

    @Test
    void sameOfferingChangeIsRejectedWithStableTargetCodeAndFailureAudit() {
        seedCatalog();
        seedOffering("source", "course-1", 2, 1);
        Enrollment source = seedActive("source", STUDENT);

        assertThatThrownBy(() -> service.changeDuringAdjustment(TOKEN,
                new ChangeOfferingCommand(source.enrollmentId(), "source", source.rowVersion())))
                .isInstanceOf(ChangeTargetInvalidException.class)
                .extracting("code").isEqualTo("COURSE_CHANGE_TARGET_INVALID");

        assertThat(enrollment(source.enrollmentId()).enrollmentStatus()).isEqualTo("ACTIVE");
        assertThat(offering("source").enrolledCount()).isEqualTo(1);
        assertThat(adjustments()).singleElement().satisfies(a -> {
            assertThat(a.operationResult()).isEqualTo("FAILED");
            assertThat(a.failureCode()).isEqualTo("COURSE_CHANGE_TARGET_INVALID");
        });
    }

    private void seedCatalog() {
        seedCatalog(NOW.minusSeconds(3600), NOW.minusSeconds(1800),
                NOW.minusSeconds(60), NOW.plusSeconds(60));
    }

    private void seedCatalog(Instant enrollmentStart, Instant enrollmentEnd,
                             Instant adjustmentStart, Instant adjustmentEnd) {
        inTransaction(connection -> {
            repository.insertTerm(connection, new Term("term-1", "2026-2027-1", "Autumn",
                    LocalDate.of(2026, 9, 1), LocalDate.of(2027, 1, 15),
                    enrollmentStart, enrollmentEnd, adjustmentStart, adjustmentEnd,
                    "ACTIVE", 0, null, null));
            String phaseType = !NOW.isBefore(adjustmentStart) && NOW.isBefore(adjustmentEnd)
                    ? "ADJUSTMENT" : (!NOW.isBefore(enrollmentStart) && NOW.isBefore(enrollmentEnd) ? "ENROLLMENT" : null);
            if (phaseType != null) repository.insertSelectionPhase(connection, new SelectionPhase(
                    "phase-1", "term-1", phaseType, "Course selection", "OPEN", 0, null, null));
            repository.insertCourse(connection, new Course("course-1", "CS101", "Programming",
                    BigDecimal.valueOf(3), 48, null, true, 0, null, null));
            return null;
        });
    }

    private void seedCourse(String id, String code) {
        inTransaction(c -> repository.insertCourse(c, new Course(id, code, code, BigDecimal.ONE,
                16, null, true, 0, null, null)));
    }

    private void seedOffering(String id, String courseId, int capacity, int count) {
        inTransaction(c -> repository.insertOffering(c, new Offering(id, "term-1", courseId, "teacher-1", id,
                capacity, count, "OPEN", 0, null, null), List.of()));
    }

    private Enrollment seedActive(String offeringId, String studentId) {
        return inTransaction(c -> repository.insertEnrollment(c, new Enrollment(UUID.randomUUID().toString(), offeringId,
                studentId, "NORMAL", "ACTIVE", NOW.minusSeconds(600), null, 0, null, null)));
    }

    private Enrollment enrollment(String id) { return inTransaction(c -> repository.requireEnrollment(c, id)); }
    private Offering offering(String id) { return inTransaction(c -> repository.requireOffering(c, id)); }
    private List<EnrollmentAdjustment> adjustments() { return inTransaction(c -> repository.findAdjustmentsByStudent(c, STUDENT)); }
    private long activeCount(String offeringId) {
        try (Connection c = connections.open(); var s = c.prepareStatement(
                "SELECT COUNT(*) FROM tblEnrollment WHERE offeringId=? AND enrollmentStatus='ACTIVE'")) {
            s.setString(1, offeringId); try (var r = s.executeQuery()) { r.next(); return r.getLong(1); }
        } catch (Exception error) { throw new IllegalStateException(error); }
    }
    private <T> T inTransaction(edu.seu.vcampus.server.persistence.SqlWork<T> work) {
        return new TransactionManager(connections).inTransaction(work);
    }
    private CourseRepository failTargetCountChange(String targetId) {
        return (CourseRepository) Proxy.newProxyInstance(CourseRepository.class.getClassLoader(),
                new Class<?>[]{CourseRepository.class}, (proxy, method, arguments) -> {
                    if ("changeEnrolledCount".equals(method.getName()) && targetId.equals(arguments[1])) {
                        throw new IllegalStateException("forced target offering-count failure");
                    }
                    try { return method.invoke(repository, arguments); }
                    catch (InvocationTargetException error) { throw error.getCause(); }
                });
    }
    private static Path schema() { return Path.of("..", "vcampus-database", "schema", "030_course.sql"); }
}
