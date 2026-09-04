package edu.seu.vcampus.server.course.service;

import edu.seu.vcampus.common.course.EnrollCommand;
import edu.seu.vcampus.common.course.EnrollmentView;
import edu.seu.vcampus.server.concurrency.ResourceKey;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.course.domain.CourseForbiddenException;
import edu.seu.vcampus.server.course.domain.DuplicateEnrollmentException;
import edu.seu.vcampus.server.course.domain.EnrollmentClosedException;
import edu.seu.vcampus.server.course.domain.OfferingFullException;
import edu.seu.vcampus.server.course.domain.ScheduleConflictException;
import edu.seu.vcampus.server.course.domain.ScheduleConflictPolicy;
import edu.seu.vcampus.server.course.domain.StudentIneligibleException;
import edu.seu.vcampus.server.course.domain.TermWindowPolicy;
import edu.seu.vcampus.server.course.repository.AccessCourseRepository;
import edu.seu.vcampus.server.course.repository.Course;
import edu.seu.vcampus.server.course.repository.CourseRepository;
import edu.seu.vcampus.server.course.repository.Enrollment;
import edu.seu.vcampus.server.course.repository.Offering;
import edu.seu.vcampus.server.course.repository.Schedule;
import edu.seu.vcampus.server.course.repository.Term;
import edu.seu.vcampus.server.course.repository.SelectionPhase;
import edu.seu.vcampus.server.persistence.ConnectionProvider;
import edu.seu.vcampus.server.persistence.TransactionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CourseEnrollmentServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-10T00:00:00Z");
    private static final String TOKEN = "student-token";
    private static final String USER_ID = "user-1";
    private static final String STUDENT_ID = "student-1";

    private final Map<String, CourseSessionIdentity> sessions = new ConcurrentHashMap<>();
    private final Map<String, StudentEnrollmentEligibility> students = new ConcurrentHashMap<>();
    private CourseRepository repository;
    private ConnectionProvider connections;
    private CourseService service;

    @BeforeEach
    void createServiceWithRealAccessPersistence() throws Exception {
        Path data = Path.of("target", "test-data");
        Files.createDirectories(data);
        String url = "jdbc:ucanaccess://" + data.resolve(UUID.randomUUID() + ".accdb")
                + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true";
        try (Connection connection = DriverManager.getConnection(url)) {
            for (String statement : Files.readString(schema()).split(";")) {
                if (!statement.isBlank()) {
                    connection.createStatement().execute(statement);
                }
            }
        }
        connections = () -> DriverManager.getConnection(url);
        repository = new AccessCourseRepository();
        sessions.put(TOKEN, new CourseSessionIdentity(USER_ID, "STUDENT"));
        students.put(USER_ID, new StudentEnrollmentEligibility(STUDENT_ID, "ACTIVE"));
        service = service(sessions::get, students::get, new StripedResourceLockManager());
    }

    @Test
    void enrollsAsNormalAtTheSuppliedServerTimeAndUpdatesCount() {
        seedCatalog("ACTIVE", NOW.minusSeconds(60), NOW.plusSeconds(60));
        seedOffering("offering-1", "course-1", 3, 0, "OPEN", List.of());

        EnrollmentView result = service.enroll(TOKEN, new EnrollCommand("offering-1"));

        assertThat(result.studentId()).isEqualTo(STUDENT_ID);
        assertThat(result.enrollmentType()).isEqualTo("NORMAL");
        assertThat(result.enrollmentStatus()).isEqualTo("ACTIVE");
        assertThat(result.enrolledAt()).isEqualTo(NOW);
        assertThat(readOffering("offering-1").enrolledCount()).isEqualTo(1);
        assertThat(activeCount("offering-1")).isEqualTo(1);
    }

    @Test void forgedNormalEnrollmentCannotBypassFailedCourseRetakeClassification() {
        seedCatalog("ACTIVE", NOW.minusSeconds(60), NOW.plusSeconds(60));
        seedOffering("offering-1", "course-1", 3, 0, "OPEN", List.of());
        inTransaction(connection -> repository.insertAttemptIfAbsent(connection, new edu.seu.vcampus.server.course.repository.CourseAttempt(
                null, STUDENT_ID, "course-1", "term-1", "FAILED", "failed", NOW)));
        assertThatThrownBy(() -> service.enroll(TOKEN, new EnrollCommand("offering-1")))
                .isInstanceOf(edu.seu.vcampus.server.course.domain.RetakeRequiredException.class);
    }

    @Test
    void usesOneCapturedInstantForTheWindowDecisionAndEnrollmentTime() {
        seedCatalog("PLANNED", NOW, NOW.plusSeconds(60));
        seedOffering("offering-1", "course-1", 3, 0, "OPEN", List.of());
        AdvancingClock clock = new AdvancingClock(NOW, NOW.plusSeconds(120));
        CourseService tickingService = service(sessions::get, students::get,
                new StripedResourceLockManager(), repository, clock);

        EnrollmentView result = tickingService.enroll(TOKEN, new EnrollCommand("offering-1"));

        assertThat(result.enrolledAt()).isEqualTo(NOW);
        assertThat(clock.instantCalls()).isOne();
    }

    @Test
    void requiresStudentRoleBeforeResolvingEligibility() {
        sessions.put(TOKEN, new CourseSessionIdentity(USER_ID, "TEACHER"));

        assertThatThrownBy(() -> service.enroll(TOKEN, new EnrollCommand("offering-1")))
                .isInstanceOf(CourseForbiddenException.class)
                .hasMessage("COMMON_FORBIDDEN: student role is required");
    }

    @Test
    void rejectsNonActiveEligibilityWithStableSafeError() {
        students.put(USER_ID, new StudentEnrollmentEligibility(STUDENT_ID, "SUSPENDED"));

        assertThatThrownBy(() -> service.enroll(TOKEN, new EnrollCommand("offering-1")))
                .isInstanceOf(StudentIneligibleException.class)
                .hasMessage("COURSE_STUDENT_INELIGIBLE: student is not eligible for enrollment");
    }

    @Test
    void rechecksEligibilityUnderLocksBeforeTransaction() {
        seedCatalog("PLANNED", NOW.minusSeconds(60), NOW.plusSeconds(60));
        seedOffering("offering-1", "course-1", 3, 0, "OPEN", List.of());
        AtomicInteger calls = new AtomicInteger();
        CourseStudentGateway changingEligibility = ignored -> calls.incrementAndGet() == 1
                ? new StudentEnrollmentEligibility(STUDENT_ID, "ACTIVE")
                : new StudentEnrollmentEligibility(STUDENT_ID, "SUSPENDED");
        CourseService changingService = service(sessions::get, changingEligibility,
                new StripedResourceLockManager());

        assertThatThrownBy(() -> changingService.enroll(TOKEN, new EnrollCommand("offering-1")))
                .isInstanceOf(StudentIneligibleException.class);
        assertThat(calls).hasValue(2);
        assertThat(activeCount("offering-1")).isZero();
        assertThat(readOffering("offering-1").enrolledCount()).isZero();
    }

    @Test
    void rechecksSessionAndRoleUnderLocksBeforeTransaction() {
        seedCatalog("PLANNED", NOW.minusSeconds(60), NOW.plusSeconds(60));
        seedOffering("offering-1", "course-1", 3, 0, "OPEN", List.of());
        AtomicInteger calls = new AtomicInteger();
        CourseAuthorizationGateway changingSession = ignored -> calls.incrementAndGet() == 1
                ? new CourseSessionIdentity(USER_ID, "STUDENT")
                : new CourseSessionIdentity(USER_ID, "TEACHER");
        CourseService changingService = service(changingSession, students::get,
                new StripedResourceLockManager());

        assertThatThrownBy(() -> changingService.enroll(TOKEN, new EnrollCommand("offering-1")))
                .isInstanceOf(CourseForbiddenException.class);
        assertThat(calls).hasValue(2);
        assertThat(activeCount("offering-1")).isZero();
        assertThat(readOffering("offering-1").enrolledCount()).isZero();
    }

    @Test
    void rejectsChangedSessionUserUnderLocksBeforeTransaction() {
        seedCatalog("PLANNED", NOW.minusSeconds(60), NOW.plusSeconds(60));
        seedOffering("offering-1", "course-1", 3, 0, "OPEN", List.of());
        students.put("user-2", new StudentEnrollmentEligibility(STUDENT_ID, "ACTIVE"));
        AtomicInteger calls = new AtomicInteger();
        CourseAuthorizationGateway changingSession = ignored -> calls.incrementAndGet() == 1
                ? new CourseSessionIdentity(USER_ID, "STUDENT")
                : new CourseSessionIdentity("user-2", "STUDENT");
        CourseService changingService = service(changingSession, students::get,
                new StripedResourceLockManager());

        assertThatThrownBy(() -> changingService.enroll(TOKEN, new EnrollCommand("offering-1")))
                .isInstanceOf(CourseForbiddenException.class);
        assertThat(calls).hasValue(2);
        assertThat(activeCount("offering-1")).isZero();
        assertThat(readOffering("offering-1").enrolledCount()).isZero();
    }

    @Test
    void rejectsChangedEligibleStudentUnderLocksBeforeTransaction() {
        seedCatalog("PLANNED", NOW.minusSeconds(60), NOW.plusSeconds(60));
        seedOffering("offering-1", "course-1", 3, 0, "OPEN", List.of());
        AtomicInteger calls = new AtomicInteger();
        CourseStudentGateway changingEligibility = ignored -> calls.incrementAndGet() == 1
                ? new StudentEnrollmentEligibility(STUDENT_ID, "ACTIVE")
                : new StudentEnrollmentEligibility("student-2", "ACTIVE");
        CourseService changingService = service(sessions::get, changingEligibility,
                new StripedResourceLockManager());

        assertThatThrownBy(() -> changingService.enroll(TOKEN, new EnrollCommand("offering-1")))
                .isInstanceOf(StudentIneligibleException.class);
        assertThat(calls).hasValue(2);
        assertThat(activeCount("offering-1")).isZero();
        assertThat(readOffering("offering-1").enrolledCount()).isZero();
    }

    @Test
    void databaseBackedGatewaysFinishBeforeTheCourseTransactionOpens() {
        seedCatalog("PLANNED", NOW.minusSeconds(60), NOW.plusSeconds(60));
        seedOffering("offering-1", "course-1", 3, 0, "OPEN", List.of());
        ConnectionProvider singleSlot = new RejectNestedConnectionProvider(connections);
        CourseAuthorizationGateway databaseAuthorization = ignored -> withGatewayConnection(
                singleSlot, () -> new CourseSessionIdentity(USER_ID, "STUDENT"));
        CourseStudentGateway databaseStudents = ignored -> withGatewayConnection(
                singleSlot, () -> new StudentEnrollmentEligibility(STUDENT_ID, "ACTIVE"));
        CourseService boundedService = new CourseServiceImpl(
                databaseAuthorization, databaseStudents, repository,
                new StripedResourceLockManager(), new TransactionManager(singleSlot),
                new TermWindowPolicy(), new ScheduleConflictPolicy(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        EnrollmentView enrolled = boundedService.enroll(TOKEN, new EnrollCommand("offering-1"));

        assertThat(enrolled.studentId()).isEqualTo(STUDENT_ID);
        assertThat(activeCount("offering-1")).isEqualTo(1);
        assertThat(readOffering("offering-1").enrolledCount()).isEqualTo(1);
    }

    @Test
    void locksStudentThenOfferingInThePublishedOrder() {
        seedCatalog("PLANNED", NOW.minusSeconds(60), NOW.plusSeconds(60));
        seedOffering("offering-1", "course-1", 3, 0, "OPEN", List.of());
        List<ResourceKey> acquired = new ArrayList<>();
        ResourceLockManager recordingLocks = new ResourceLockManager() {
            @Override
            public <T> T withLocks(List<ResourceKey> orderedKeys, Supplier<T> action) {
                acquired.addAll(orderedKeys);
                return action.get();
            }
        };

        service(sessions::get, students::get, recordingLocks)
                .enroll(TOKEN, new EnrollCommand("offering-1"));

        assertThat(acquired).containsExactly(
                new ResourceKey("STUDENT", STUDENT_ID),
                new ResourceKey("OFFERING", "offering-1"));
    }

    @Test
    void rejectsClosedOfferingAndClosedWindowWithEnrollmentCode() {
        seedCatalog("CLOSED", NOW.minusSeconds(60), NOW.plusSeconds(60));
        seedOffering("closed-offering", "course-1", 3, 0, "CLOSED", List.of());
        seedOffering("open-offering", "course-1", 3, 0, "OPEN", List.of());

        assertThatThrownBy(() -> service.enroll(TOKEN, new EnrollCommand("closed-offering")))
                .isInstanceOf(EnrollmentClosedException.class)
                .extracting("code").isEqualTo("COURSE_ENROLLMENT_NOT_OPEN");
        assertThatThrownBy(() -> service.enroll(TOKEN, new EnrollCommand("open-offering")))
                .isInstanceOf(EnrollmentClosedException.class)
                .extracting("code").isEqualTo("COURSE_ENROLLMENT_NOT_OPEN");
    }

    @Test
    void rejectsFullOfferingWithoutWritingEnrollment() {
        seedCatalog("PLANNED", NOW.minusSeconds(60), NOW.plusSeconds(60));
        seedOffering("offering-1", "course-1", 1, 1, "OPEN", List.of());
        seedActive("offering-1", "someone-else");

        assertThatThrownBy(() -> service.enroll(TOKEN, new EnrollCommand("offering-1")))
                .isInstanceOf(OfferingFullException.class)
                .extracting("code").isEqualTo("COURSE_OFFERING_FULL");
        assertThat(activeCount("offering-1")).isEqualTo(1);
    }

    @Test
    void rejectsSameCourseInTermEvenWhenOfferingDiffers() {
        seedCatalog("PLANNED", NOW.minusSeconds(60), NOW.plusSeconds(60));
        seedOffering("selected-offering", "course-1", 3, 1, "OPEN", List.of());
        seedOffering("target-offering", "course-1", 3, 0, "OPEN", List.of());
        seedActive("selected-offering", STUDENT_ID);

        assertThatThrownBy(() -> service.enroll(TOKEN, new EnrollCommand("target-offering")))
                .isInstanceOf(DuplicateEnrollmentException.class)
                .extracting("code").isEqualTo("COURSE_DUPLICATE_ENROLLMENT");
        assertThat(activeCount("target-offering")).isZero();
    }

    @Test
    void rejectsOnlyARealThreeDimensionalScheduleConflict() {
        seedCatalog("PLANNED", NOW.minusSeconds(60), NOW.plusSeconds(60));
        seedCourse("course-2", "CS102");
        seedOffering("selected-offering", "course-1", 3, 1, "OPEN",
                List.of(schedule("selected", "selected-offering", DayOfWeek.MONDAY, 1, 2, 1, 16)));
        seedOffering("conflicting-offering", "course-2", 3, 0, "OPEN",
                List.of(schedule("conflict", "conflicting-offering", DayOfWeek.MONDAY, 2, 3, 8, 12)));
        seedActive("selected-offering", STUDENT_ID);

        assertThatThrownBy(() -> service.enroll(TOKEN, new EnrollCommand("conflicting-offering")))
                .isInstanceOf(ScheduleConflictException.class)
                .extracting("code").isEqualTo("COURSE_SCHEDULE_CONFLICT");
        assertThat(activeCount("conflicting-offering")).isZero();
    }

    @Test
    void reactivatesDroppedRowAsNormalAndKeepsItsStableId() {
        seedCatalog("PLANNED", NOW.minusSeconds(60), NOW.plusSeconds(60));
        seedOffering("offering-1", "course-1", 3, 0, "OPEN", List.of());
        Enrollment original = inTransaction(connection -> repository.insertEnrollment(connection,
                new Enrollment("retained-enrollment", "offering-1", STUDENT_ID, "LATE_ADD", "DROPPED",
                        NOW.minusSeconds(600), NOW.minusSeconds(300), 0, null, null)));

        EnrollmentView result = service.enroll(TOKEN, new EnrollCommand("offering-1"));

        assertThat(result.enrollmentId()).isEqualTo(original.enrollmentId());
        assertThat(result.enrollmentType()).isEqualTo("NORMAL");
        assertThat(result.enrollmentStatus()).isEqualTo("ACTIVE");
        assertThat(result.droppedAt()).isNull();
        assertThat(result.rowVersion()).isEqualTo(original.rowVersion() + 1);
        assertThat(readOffering("offering-1").enrolledCount()).isEqualTo(1);
    }

    @Test
    void rollsBackNewEnrollmentWhenCountChangeFails() {
        seedCatalog("PLANNED", NOW.minusSeconds(60), NOW.plusSeconds(60));
        seedOffering("offering-1", "course-1", 3, 0, "OPEN", List.of());
        CourseService failingService = service(sessions::get, students::get,
                new StripedResourceLockManager(), failOnCountChange(repository),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> failingService.enroll(TOKEN, new EnrollCommand("offering-1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("forced enrollment-count failure");

        assertThat(enrollmentCount("offering-1")).isZero();
        assertThat(activeCount("offering-1")).isZero();
        assertThat(readOffering("offering-1").enrolledCount()).isZero();
    }

    @Test
    void rollsBackReactivationWhenCountChangeFails() {
        seedCatalog("PLANNED", NOW.minusSeconds(60), NOW.plusSeconds(60));
        seedOffering("offering-1", "course-1", 3, 0, "OPEN", List.of());
        inTransaction(connection -> repository.insertEnrollment(connection,
                new Enrollment("retained-enrollment", "offering-1", STUDENT_ID, "LATE_ADD", "DROPPED",
                        NOW.minusSeconds(600), NOW.minusSeconds(300), 0, null, null)));
        CourseService failingService = service(sessions::get, students::get,
                new StripedResourceLockManager(), failOnCountChange(repository),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> failingService.enroll(TOKEN, new EnrollCommand("offering-1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("forced enrollment-count failure");

        Enrollment retained = inTransaction(connection -> repository.findEnrollment(
                connection, STUDENT_ID, "offering-1").orElseThrow());
        assertThat(retained.enrollmentStatus()).isEqualTo("DROPPED");
        assertThat(retained.enrollmentType()).isEqualTo("LATE_ADD");
        assertThat(retained.droppedAt()).isEqualTo(NOW.minusSeconds(300));
        assertThat(activeCount("offering-1")).isZero();
        assertThat(readOffering("offering-1").enrolledCount()).isZero();
    }

    private CourseService service(CourseAuthorizationGateway authorization,
                                  CourseStudentGateway studentGateway,
                                  ResourceLockManager lockManager) {
        return service(authorization, studentGateway, lockManager, repository,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private CourseService service(CourseAuthorizationGateway authorization,
                                  CourseStudentGateway studentGateway,
                                  ResourceLockManager lockManager,
                                  CourseRepository courseRepository,
                                  Clock courseClock) {
        return new CourseServiceImpl(authorization, studentGateway, courseRepository, lockManager,
                new TransactionManager(connections), new TermWindowPolicy(),
                new ScheduleConflictPolicy(), courseClock);
    }

    private void seedCatalog(String termStatus, Instant enrollmentStart, Instant enrollmentEnd) {
        inTransaction(connection -> {
            String persistedStatus = "PLANNED".equals(termStatus) ? "ACTIVE" : termStatus;
            repository.insertTerm(connection, new Term("term-1", "2026-2027-1", "Autumn",
                    LocalDate.of(2026, 9, 1), LocalDate.of(2027, 1, 15),
                    enrollmentStart, enrollmentEnd, NOW.plusSeconds(120), NOW.plusSeconds(180),
                    persistedStatus, 0, null, null));
            if ("ACTIVE".equals(persistedStatus) && !NOW.isBefore(enrollmentStart) && NOW.isBefore(enrollmentEnd)) {
                repository.insertSelectionPhase(connection, new SelectionPhase("phase-1", "term-1", "ENROLLMENT",
                        "Autumn course selection", "OPEN", 0, null, null));
            }
            repository.insertCourse(connection, new Course("course-1", "CS101", "Programming",
                    BigDecimal.valueOf(3), 48, null, true, 0, null, null));
            return null;
        });
    }

    private void seedCourse(String courseId, String courseCode) {
        inTransaction(connection -> repository.insertCourse(connection,
                new Course(courseId, courseCode, "Course " + courseCode, BigDecimal.valueOf(2),
                        32, null, true, 0, null, null)));
    }

    private void seedOffering(String offeringId, String courseId, int capacity, int enrolledCount,
                              String status, List<Schedule> schedules) {
        inTransaction(connection -> repository.insertOffering(connection,
                new Offering(offeringId, "term-1", courseId, "teacher-1", offeringId,
                        capacity, enrolledCount, status, 0, null, null), schedules));
    }

    private void seedActive(String offeringId, String studentId) {
        inTransaction(connection -> repository.insertEnrollment(connection,
                new Enrollment(UUID.randomUUID().toString(), offeringId, studentId, "NORMAL", "ACTIVE",
                        NOW.minusSeconds(300), null, 0, null, null)));
    }

    private Offering readOffering(String offeringId) {
        return inTransaction(connection -> repository.requireOffering(connection, offeringId));
    }

    private long activeCount(String offeringId) {
        return enrollmentCountWhere(offeringId, " AND enrollmentStatus='ACTIVE'");
    }

    private long enrollmentCount(String offeringId) {
        return enrollmentCountWhere(offeringId, "");
    }

    private long enrollmentCountWhere(String offeringId, String predicate) {
        try (Connection connection = connections.open();
             var statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM tblEnrollment WHERE offeringId=?" + predicate)) {
            statement.setString(1, offeringId);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private <T> T inTransaction(edu.seu.vcampus.server.persistence.SqlWork<T> work) {
        return new TransactionManager(connections).inTransaction(work);
    }

    private static <T> T withGatewayConnection(ConnectionProvider provider, Supplier<T> work) {
        try (Connection ignored = provider.open()) {
            return work.get();
        } catch (SQLException error) {
            throw new IllegalStateException("gateway connection failed", error);
        }
    }

    private static CourseRepository failOnCountChange(CourseRepository delegate) {
        return (CourseRepository) Proxy.newProxyInstance(
                CourseRepository.class.getClassLoader(), new Class<?>[]{CourseRepository.class},
                (proxy, method, arguments) -> {
                    if ("changeEnrolledCount".equals(method.getName())) {
                        throw new IllegalStateException("forced enrollment-count failure");
                    }
                    try {
                        return method.invoke(delegate, arguments);
                    } catch (InvocationTargetException error) {
                        throw error.getCause();
                    }
                });
    }

    private static Schedule schedule(String id, String offeringId, DayOfWeek day,
                                     int startPeriod, int endPeriod, int startWeek, int endWeek) {
        return new Schedule(id, offeringId, day, startPeriod, endPeriod,
                startWeek, endWeek, "A101");
    }

    private static Path schema() {
        return Path.of("..", "vcampus-database", "schema", "030_course.sql");
    }

    private static final class RejectNestedConnectionProvider implements ConnectionProvider {
        private final ConnectionProvider delegate;
        private final AtomicBoolean inUse = new AtomicBoolean();

        private RejectNestedConnectionProvider(ConnectionProvider delegate) {
            this.delegate = delegate;
        }

        @Override
        public Connection open() throws SQLException {
            if (!inUse.compareAndSet(false, true)) {
                throw new SQLException("nested connection acquisition rejected");
            }
            Connection actual;
            try {
                actual = delegate.open();
            } catch (SQLException error) {
                inUse.set(false);
                throw error;
            }
            AtomicBoolean closed = new AtomicBoolean();
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, arguments) -> {
                        if ("close".equals(method.getName()) && closed.compareAndSet(false, true)) {
                            try {
                                return method.invoke(actual, arguments);
                            } finally {
                                inUse.set(false);
                            }
                        }
                        try {
                            return method.invoke(actual, arguments);
                        } catch (InvocationTargetException error) {
                            throw error.getCause();
                        }
                    });
        }
    }

    private static final class AdvancingClock extends Clock {
        private final Instant first;
        private final Instant later;
        private final AtomicInteger calls = new AtomicInteger();

        private AdvancingClock(Instant first, Instant later) {
            this.first = first;
            this.later = later;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return calls.getAndIncrement() == 0 ? first : later;
        }

        private int instantCalls() {
            return calls.get();
        }
    }
}
