package edu.seu.vcampus.server.course.service;

import edu.seu.vcampus.common.course.CourseOutcome;
import edu.seu.vcampus.common.course.ImportCourseOutcomesCommand;
import edu.seu.vcampus.common.course.RetakeEligibility;
import edu.seu.vcampus.common.course.RetakeCommand;
import edu.seu.vcampus.common.course.EnrollmentView;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.course.domain.ScheduleConflictPolicy;
import edu.seu.vcampus.server.course.domain.TermWindowPolicy;
import edu.seu.vcampus.server.course.domain.OutcomeImportInvalidException;
import edu.seu.vcampus.server.course.domain.RetakeNotEligibleException;
import edu.seu.vcampus.server.course.domain.CourseForbiddenException;
import edu.seu.vcampus.server.course.domain.CourseRuleException;
import edu.seu.vcampus.server.course.domain.DuplicateEnrollmentException;
import edu.seu.vcampus.server.course.domain.EnrollmentClosedException;
import edu.seu.vcampus.server.course.domain.OfferingFullException;
import edu.seu.vcampus.server.course.domain.ScheduleConflictException;
import edu.seu.vcampus.server.course.domain.StudentIneligibleException;
import edu.seu.vcampus.server.course.repository.AccessCourseRepository;
import edu.seu.vcampus.server.course.repository.Course;
import edu.seu.vcampus.server.course.repository.CourseRepository;
import edu.seu.vcampus.server.course.repository.Term;
import edu.seu.vcampus.server.course.repository.SelectionPhase;
import edu.seu.vcampus.server.course.repository.Offering;
import edu.seu.vcampus.server.course.repository.Enrollment;
import edu.seu.vcampus.server.course.repository.Schedule;
import edu.seu.vcampus.server.persistence.ConnectionProvider;
import edu.seu.vcampus.server.persistence.TransactionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetakeServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-10T00:00:00Z");
    private static final String TOKEN = "student-token";
    private static final String USER_ID = "user-1";
    private static final String STUDENT_ID = "student-1";

    private CourseRepository repository;
    private ConnectionProvider connections;
    private CourseService service;
    private final Map<String, CourseSessionIdentity> sessions = new ConcurrentHashMap<>();
    private final Map<String, StudentEnrollmentEligibility> studentRecords = new ConcurrentHashMap<>();

    @BeforeEach
    void createServiceWithRealAccessPersistence() throws Exception {
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
        sessions.put(TOKEN, new CourseSessionIdentity(USER_ID, "STUDENT"));
        studentRecords.put(USER_ID, new StudentEnrollmentEligibility(STUDENT_ID, "ACTIVE"));
        service = createService(sessions::get, studentRecords::get,
                new StripedResourceLockManager());
        seedCatalog();
    }

    @Test
    void passedHistoryOverridesFailedHistoryForRetakeEligibility() {
        service.importCourseOutcomes(new ImportCourseOutcomesCommand(List.of(
                new ImportCourseOutcomesCommand.OutcomeEntry(
                        STUDENT_ID, "course-1", "term-1", CourseOutcome.FAILED, "failed-source"),
                new ImportCourseOutcomesCommand.OutcomeEntry(
                        STUDENT_ID, "course-1", "term-1", CourseOutcome.PASSED, "passed-source"))));

        RetakeEligibility result = service.checkRetakeEligibility(TOKEN, "course-1");

        assertThat(result.courseId()).isEqualTo("course-1");
        assertThat(result.eligible()).isFalse();
        assertThat(result.failedAttemptIds()).hasSize(1).allMatch(id -> !id.isBlank());
        assertThat(result.reason()).isEqualTo("COURSE_RETAKE_NOT_ELIGIBLE");
    }

    @Test
    void forgedRetakeIsRejectedWhenTheCourseWasLaterPassed() {
        service.importCourseOutcomes(new ImportCourseOutcomesCommand(List.of(
                new ImportCourseOutcomesCommand.OutcomeEntry(STUDENT_ID, "course-1", "term-1", CourseOutcome.FAILED, "failed"),
                new ImportCourseOutcomesCommand.OutcomeEntry(STUDENT_ID, "course-1", "term-1", CourseOutcome.PASSED, "passed"))));
        seedOffering("offering-1", "course-1", 2, 0, "OPEN");
        assertThatThrownBy(() -> service.enrollRetake(TOKEN, new RetakeCommand("offering-1")))
                .isInstanceOf(edu.seu.vcampus.server.course.domain.CourseAlreadyPassedException.class);
    }

    @Test
    void passedOnlyAndNoHistoryCarryTheStableIneligibleReason() {
        service.importCourseOutcomes(new ImportCourseOutcomesCommand(List.of(
                new ImportCourseOutcomesCommand.OutcomeEntry(
                        STUDENT_ID, "course-1", "term-1", CourseOutcome.PASSED, "passed-source"))));

        assertThat(service.checkRetakeEligibility(TOKEN, "course-1"))
                .extracting(RetakeEligibility::eligible, RetakeEligibility::reason)
                .containsExactly(false, "COURSE_RETAKE_NOT_ELIGIBLE");
        assertThat(service.checkRetakeEligibility(TOKEN, "course-2"))
                .extracting(RetakeEligibility::eligible, RetakeEligibility::failedAttemptIds,
                        RetakeEligibility::reason)
                .containsExactly(false, List.of(), "COURSE_RETAKE_NOT_ELIGIBLE");
    }

    @Test
    void rejectsOutcomeImportForUnknownStudentWithoutWritingAttempts() {
        var command = new ImportCourseOutcomesCommand(List.of(
                new ImportCourseOutcomesCommand.OutcomeEntry(
                        "missing-student", "course-1", "term-1", CourseOutcome.FAILED, "unknown-source")));

        assertThatThrownBy(() -> service.importCourseOutcomes(command))
                .isInstanceOf(OutcomeImportInvalidException.class)
                .extracting("code").isEqualTo("COURSE_OUTCOME_IMPORT_INVALID");
        assertThat(attemptCount()).isZero();
    }

    @Test
    void enrollsEligibleFailedCourseThroughThePipelineAsRetake() {
        importOutcome(CourseOutcome.FAILED, "failed-source");
        seedOffering("offering-1", "course-1", 2, 0, "OPEN");

        EnrollmentView result = service.enrollRetake(TOKEN, new RetakeCommand("offering-1"));

        assertThat(result.enrollmentType()).isEqualTo("RETAKE");
        assertThat(result.studentId()).isEqualTo(STUDENT_ID);
        assertThat(activeCount("offering-1")).isOne();
        assertThat(readOffering("offering-1").enrolledCount()).isOne();
    }

    @Test
    void rejectsRetakeForAnAlreadyPassedCourseUsingTheStableCode() {
        importOutcome(CourseOutcome.PASSED, "passed-source");
        seedOffering("offering-1", "course-1", 2, 0, "OPEN");

        assertThatThrownBy(() -> service.enrollRetake(TOKEN, new RetakeCommand("offering-1")))
                .isInstanceOf(edu.seu.vcampus.server.course.domain.CourseAlreadyPassedException.class)
                .extracting("code").isEqualTo("COURSE_ALREADY_PASSED");
        assertThat(activeCount("offering-1")).isZero();
        assertThat(readOffering("offering-1").enrolledCount()).isZero();
    }

    @Test
    void conflictingReplayIsRejectedWithoutOverwritingTheOriginalOutcome() {
        importOutcome(CourseOutcome.FAILED, "shared-source");
        ImportCourseOutcomesCommand conflict = new ImportCourseOutcomesCommand(List.of(
                new ImportCourseOutcomesCommand.OutcomeEntry(
                        STUDENT_ID, "course-1", "term-1", CourseOutcome.PASSED, "shared-source")));

        assertThatThrownBy(() -> service.importCourseOutcomes(conflict))
                .isInstanceOf(OutcomeImportInvalidException.class)
                .extracting("code").isEqualTo("COURSE_OUTCOME_IMPORT_INVALID");
        List<edu.seu.vcampus.server.course.repository.CourseAttempt> retained =
                new TransactionManager(connections).inTransaction(connection ->
                        repository.findAttempts(connection, STUDENT_ID, "course-1"));
        assertThat(retained)
                .singleElement().extracting("outcome").isEqualTo("FAILED");
    }

    @Test
    void identicalImportReplayIsIdempotentIncludingDuplicatesInsideOneBatch() {
        ImportCourseOutcomesCommand.OutcomeEntry entry = new ImportCourseOutcomesCommand.OutcomeEntry(
                STUDENT_ID, "course-1", "term-1", CourseOutcome.FAILED, "stable-source");

        service.importCourseOutcomes(new ImportCourseOutcomesCommand(List.of(entry, entry)));
        service.importCourseOutcomes(new ImportCourseOutcomesCommand(List.of(entry)));

        assertThat(attemptCount()).isOne();
        assertThat(service.checkRetakeEligibility(TOKEN, "course-1").failedAttemptIds())
                .hasSize(1);
    }

    @Test
    void foreignKeyFailureMapsToImportInvalidAndRollsBackTheWholeBatch() {
        ImportCourseOutcomesCommand batch = new ImportCourseOutcomesCommand(List.of(
                new ImportCourseOutcomesCommand.OutcomeEntry(
                        STUDENT_ID, "course-1", "term-1", CourseOutcome.FAILED, "valid-first"),
                new ImportCourseOutcomesCommand.OutcomeEntry(
                        STUDENT_ID, "missing-course", "term-1", CourseOutcome.FAILED, "invalid-second")));

        assertThatThrownBy(() -> service.importCourseOutcomes(batch))
                .isInstanceOf(OutcomeImportInvalidException.class)
                .extracting("code").isEqualTo("COURSE_OUTCOME_IMPORT_INVALID");
        assertThat(attemptCount()).isZero();
    }

    @Test
    void retakeReusesClosedAndSameCourseDuplicateRules() {
        importOutcome(CourseOutcome.FAILED, "failed-source");
        seedOffering("closed", "course-1", 2, 0, "CLOSED");
        seedOffering("same-course", "course-1", 2, 0, "OPEN");
        seedOffering("already-selected", "course-1", 2, 1, "OPEN");
        seedActive("already-selected", STUDENT_ID);

        assertThatThrownBy(() -> service.enrollRetake(TOKEN, new RetakeCommand("closed")))
                .isInstanceOf(EnrollmentClosedException.class);
        assertThatThrownBy(() -> service.enrollRetake(TOKEN, new RetakeCommand("same-course")))
                .isInstanceOf(DuplicateEnrollmentException.class);
        assertThat(activeCount("closed") + activeCount("same-course")).isZero();
    }

    @Test
    void retakeFullAndConflictRulesAreReachedWhenNoSameCourseIsAlreadyActive() {
        importOutcome(CourseOutcome.FAILED, "failed-source");
        seedOffering("full", "course-1", 1, 1, "OPEN");
        assertThatThrownBy(() -> service.enrollRetake(TOKEN, new RetakeCommand("full")))
                .isInstanceOf(OfferingFullException.class);

        seedOffering("selected", "course-2", 2, 1, "OPEN",
                List.of(schedule("selected-time", "selected", DayOfWeek.MONDAY, 1, 2)));
        seedOffering("conflict", "course-1", 2, 0, "OPEN",
                List.of(schedule("conflict-time", "conflict", DayOfWeek.MONDAY, 2, 3)));
        seedActive("selected", STUDENT_ID);
        assertThatThrownBy(() -> service.enrollRetake(TOKEN, new RetakeCommand("conflict")))
                .isInstanceOf(ScheduleConflictException.class);
        assertThat(activeCount("conflict")).isZero();
    }

    @Test
    void retakeRejectsClosedTermAndOutsideEnrollmentWindow() {
        importOutcome(CourseOutcome.FAILED, "failed-source");
        seedTerm("closed-term", "2026-2027-1", "CLOSED",
                NOW.minusSeconds(60), NOW.plusSeconds(60));
        seedTerm("future-term", "2026-2027-2", "PLANNED",
                NOW.plusSeconds(60), NOW.plusSeconds(120));
        seedOffering("closed-term-offering", "closed-term", "course-1", 2, 0, "OPEN", List.of());
        seedOffering("future-offering", "future-term", "course-1", 2, 0, "OPEN", List.of());

        assertThatThrownBy(() -> service.enrollRetake(
                TOKEN, new RetakeCommand("closed-term-offering")))
                .isInstanceOf(EnrollmentClosedException.class)
                .extracting("code").isEqualTo("COURSE_ENROLLMENT_NOT_OPEN");
        assertThatThrownBy(() -> service.enrollRetake(
                TOKEN, new RetakeCommand("future-offering")))
                .isInstanceOf(EnrollmentClosedException.class)
                .extracting("code").isEqualTo("COURSE_ENROLLMENT_NOT_OPEN");
        assertThat(activeCount("closed-term-offering") + activeCount("future-offering")).isZero();
    }

    @Test
    void eligibilityAndEnrollmentRejectLockedUserAndStudentIdentityDrift() {
        importOutcome(CourseOutcome.FAILED, "failed-source");
        seedOffering("offering-1", "course-1", 2, 0, "OPEN");
        studentRecords.put("user-2", new StudentEnrollmentEligibility(STUDENT_ID, "ACTIVE"));
        AtomicInteger userCalls = new AtomicInteger();
        CourseService userDrift = createService(ignored -> userCalls.incrementAndGet() == 1
                        ? new CourseSessionIdentity(USER_ID, "STUDENT")
                        : new CourseSessionIdentity("user-2", "STUDENT"),
                studentRecords::get, new StripedResourceLockManager());

        assertThatThrownBy(() -> userDrift.checkRetakeEligibility(TOKEN, "course-1"))
                .isInstanceOf(CourseForbiddenException.class);

        AtomicInteger studentCalls = new AtomicInteger();
        CourseService studentDrift = createService(sessions::get, ignored ->
                        studentCalls.incrementAndGet() == 1
                                ? new StudentEnrollmentEligibility(STUDENT_ID, "ACTIVE")
                                : new StudentEnrollmentEligibility("student-2", "ACTIVE"),
                new StripedResourceLockManager());
        assertThatThrownBy(() -> studentDrift.enrollRetake(TOKEN, new RetakeCommand("offering-1")))
                .isInstanceOf(StudentIneligibleException.class);
        assertThat(activeCount("offering-1")).isZero();
        assertThat(readOffering("offering-1").enrolledCount()).isZero();
    }

    @Test
    void eligibilityRejectsStudentDriftAndEnrollmentRejectsUserDrift() {
        importOutcome(CourseOutcome.FAILED, "failed-source");
        seedOffering("offering-1", "course-1", 2, 0, "OPEN");
        AtomicInteger studentCalls = new AtomicInteger();
        CourseService studentDrift = createService(sessions::get, ignored ->
                        studentCalls.incrementAndGet() == 1
                                ? new StudentEnrollmentEligibility(STUDENT_ID, "ACTIVE")
                                : new StudentEnrollmentEligibility("student-2", "ACTIVE"),
                new StripedResourceLockManager());
        assertThatThrownBy(() -> studentDrift.checkRetakeEligibility(TOKEN, "course-1"))
                .isInstanceOf(StudentIneligibleException.class);

        studentRecords.put("user-2", new StudentEnrollmentEligibility(STUDENT_ID, "ACTIVE"));
        AtomicInteger userCalls = new AtomicInteger();
        CourseService userDrift = createService(ignored -> userCalls.incrementAndGet() == 1
                        ? new CourseSessionIdentity(USER_ID, "STUDENT")
                        : new CourseSessionIdentity("user-2", "STUDENT"),
                studentRecords::get, new StripedResourceLockManager());
        assertThatThrownBy(() -> userDrift.enrollRetake(TOKEN, new RetakeCommand("offering-1")))
                .isInstanceOf(CourseForbiddenException.class);
        assertThat(activeCount("offering-1")).isZero();
        assertThat(readOffering("offering-1").enrolledCount()).isZero();
    }

    @Test
    void sharedLocksAllowExactlyOneWinnerForTheLastRetakeSeat() throws Exception {
        int clients = Integer.getInteger("course.test.concurrentClients", 20);
        seedOffering("offering-1", "course-1", 1, 0, "OPEN");
        List<ImportCourseOutcomesCommand.OutcomeEntry> outcomes = new ArrayList<>();
        for (int index = 0; index < clients; index++) {
            String token = "token-" + index;
            String user = "user-concurrent-" + index;
            String student = "student-concurrent-" + index;
            sessions.put(token, new CourseSessionIdentity(user, "STUDENT"));
            studentRecords.put(user, new StudentEnrollmentEligibility(student, "ACTIVE"));
            outcomes.add(new ImportCourseOutcomesCommand.OutcomeEntry(
                    student, "course-1", "term-1", CourseOutcome.FAILED, "source-" + index));
        }
        service.importCourseOutcomes(new ImportCourseOutcomesCommand(outcomes));
        StripedResourceLockManager sharedLocks = new StripedResourceLockManager();
        List<CourseService> services = new ArrayList<>();
        for (int index = 0; index < clients; index++) {
            services.add(createService(sessions::get, studentRecords::get, sharedLocks));
        }

        List<Outcome<EnrollmentView>> results = concurrently(clients, index ->
                services.get(index).enrollRetake("token-" + index, new RetakeCommand("offering-1")));

        assertThat(results.stream().filter(Outcome::success)).hasSize(1);
        assertThat(results.stream().filter(result -> !result.success()))
                .extracting(result -> ((CourseRuleException) result.failure()).code())
                .containsOnly("COURSE_OFFERING_FULL");
        assertThat(activeCount("offering-1")).isOne();
        assertThat(readOffering("offering-1").enrolledCount()).isOne();
    }

    @Test
    void sameStudentConcurrentRetakeHasOneSuccessAndTypedDuplicateLosers() throws Exception {
        int clients = Integer.getInteger("course.test.concurrentClients", 20);
        importOutcome(CourseOutcome.FAILED, "failed-source");
        seedOffering("offering-1", "course-1", clients, 0, "OPEN");

        List<Outcome<EnrollmentView>> results = concurrently(clients, ignored ->
                service.enrollRetake(TOKEN, new RetakeCommand("offering-1")));

        assertThat(results.stream().filter(Outcome::success)).hasSize(1);
        assertThat(results.stream().filter(result -> !result.success()))
                .extracting(result -> ((CourseRuleException) result.failure()).code())
                .containsOnly("COURSE_DUPLICATE_ENROLLMENT");
        assertThat(activeCount("offering-1")).isOne();
        assertThat(readOffering("offering-1").enrolledCount()).isOne();
    }

    @Test
    void sharedLocksMakeConcurrentIdenticalSourceImportsIdempotentAcrossServices() throws Exception {
        int clients = Integer.getInteger("course.test.concurrentClients", 20);
        ImportCourseOutcomesCommand command = new ImportCourseOutcomesCommand(List.of(
                new ImportCourseOutcomesCommand.OutcomeEntry(
                        STUDENT_ID, "course-1", "term-1", CourseOutcome.FAILED, "shared-source")));
        StripedResourceLockManager sharedLocks = new StripedResourceLockManager();
        List<CourseService> services = new ArrayList<>();
        for (int index = 0; index < clients; index++) {
            services.add(createService(sessions::get, studentRecords::get, sharedLocks));
        }

        List<Outcome<Void>> results = concurrently(clients, index -> {
            services.get(index).importCourseOutcomes(command);
            return null;
        });

        assertThat(results).allMatch(Outcome::success);
        assertThat(attemptCount()).isOne();
        edu.seu.vcampus.server.course.repository.CourseAttempt persisted =
                new TransactionManager(connections).inTransaction(connection ->
                        repository.findAttemptBySourceReference(
                                connection, "shared-source").orElseThrow());
        assertThat(persisted)
                .extracting("studentId", "courseId", "termId", "outcome", "sourceReference")
                .containsExactly(STUDENT_ID, "course-1", "term-1", "FAILED", "shared-source");
    }

    @Test
    void sharedLocksRejectConcurrentConflictingPayloadForTheSameSource() throws Exception {
        ImportCourseOutcomesCommand failed = new ImportCourseOutcomesCommand(List.of(
                new ImportCourseOutcomesCommand.OutcomeEntry(
                        STUDENT_ID, "course-1", "term-1", CourseOutcome.FAILED, "shared-source")));
        ImportCourseOutcomesCommand passed = new ImportCourseOutcomesCommand(List.of(
                new ImportCourseOutcomesCommand.OutcomeEntry(
                        STUDENT_ID, "course-1", "term-1", CourseOutcome.PASSED, "shared-source")));
        StripedResourceLockManager sharedLocks = new StripedResourceLockManager();
        List<CourseService> services = List.of(
                createService(sessions::get, studentRecords::get, sharedLocks),
                createService(sessions::get, studentRecords::get, sharedLocks));

        List<Outcome<Void>> results = concurrently(2, index -> {
            services.get(index).importCourseOutcomes(index == 0 ? failed : passed);
            return null;
        });

        int winnerIndex = results.get(0).success() ? 0 : 1;
        int loserIndex = 1 - winnerIndex;
        assertThat(results.get(winnerIndex).success()).isTrue();
        assertThat(results.get(loserIndex).failure())
                .isInstanceOf(CourseRuleException.class)
                .extracting("code").isEqualTo("COURSE_OUTCOME_IMPORT_INVALID");
        assertThat(attemptCount()).isOne();
        edu.seu.vcampus.server.course.repository.CourseAttempt persisted =
                new TransactionManager(connections).inTransaction(connection ->
                        repository.findAttemptBySourceReference(
                                connection, "shared-source").orElseThrow());
        assertThat(persisted)
                .extracting("sourceReference", "outcome")
                .containsExactly("shared-source", winnerIndex == 0 ? "FAILED" : "PASSED");
    }

    @Test
    void retakeReactivatesTheRetainedNaturalKeyAsRetakeWithoutAddingAnotherRow() {
        importOutcome(CourseOutcome.FAILED, "failed-source");
        seedOffering("offering-1", "course-1", 2, 0, "OPEN");
        new TransactionManager(connections).inTransaction(connection -> repository.insertEnrollment(
                connection, new Enrollment("retained-enrollment", "offering-1", STUDENT_ID,
                        "NORMAL", "DROPPED", NOW.minusSeconds(600), NOW.minusSeconds(300),
                        0, null, null)));

        EnrollmentView result = service.enrollRetake(TOKEN, new RetakeCommand("offering-1"));
        Enrollment persisted = new TransactionManager(connections).inTransaction(
                connection -> repository.requireEnrollment(connection, result.enrollmentId()));

        assertThat(result.enrollmentId()).isEqualTo("retained-enrollment");
        assertThat(result.enrollmentType()).isEqualTo("RETAKE");
        assertThat(result.enrollmentStatus()).isEqualTo("ACTIVE");
        assertThat(result.droppedAt()).isNull();
        assertThat(persisted.enrollmentId()).isEqualTo("retained-enrollment");
        assertThat(persisted.enrollmentType()).isEqualTo("RETAKE");
        assertThat(persisted.enrollmentStatus()).isEqualTo("ACTIVE");
        assertThat(persisted.droppedAt()).isNull();
        assertThat(naturalKeyCount(STUDENT_ID, "offering-1")).isOne();
        assertThat(activeCount("offering-1")).isOne();
        assertThat(readOffering("offering-1").enrolledCount()).isEqualTo(activeCount("offering-1"));
    }

    private void seedCatalog() {
        new TransactionManager(connections).inTransaction(connection -> {
            repository.insertTerm(connection, new Term("term-1", "2025-2026-2", "Previous",
                    LocalDate.of(2026, 2, 1), LocalDate.of(2026, 6, 30),
                    NOW.minusSeconds(7200), NOW.plusSeconds(7200),
                    NOW.plusSeconds(10800), NOW.plusSeconds(14400), "ACTIVE", 0, null, null));
            repository.insertSelectionPhase(connection, new SelectionPhase("phase-1", "term-1", "ENROLLMENT",
                    "Retake selection", "OPEN", 0, null, null));
            repository.insertCourse(connection, new Course("course-1", "CS101", "Programming",
                    BigDecimal.valueOf(3), 48, null, true, 0, null, null));
            repository.insertCourse(connection, new Course("course-2", "CS102", "Algorithms",
                    BigDecimal.valueOf(3), 48, null, true, 0, null, null));
            return null;
        });
    }

    private void importOutcome(CourseOutcome outcome, String sourceReference) {
        service.importCourseOutcomes(new ImportCourseOutcomesCommand(List.of(
                new ImportCourseOutcomesCommand.OutcomeEntry(
                        STUDENT_ID, "course-1", "term-1", outcome, sourceReference))));
    }

    private void seedOffering(String offeringId, String courseId, int capacity,
                              int enrolledCount, String status) {
        seedOffering(offeringId, courseId, capacity, enrolledCount, status, List.of());
    }

    private void seedOffering(String offeringId, String courseId, int capacity,
                              int enrolledCount, String status, List<Schedule> schedules) {
        seedOffering(offeringId, "term-1", courseId, capacity, enrolledCount, status, schedules);
    }

    private void seedOffering(String offeringId, String termId, String courseId, int capacity,
                              int enrolledCount, String status, List<Schedule> schedules) {
        new TransactionManager(connections).inTransaction(connection -> repository.insertOffering(
                connection, new Offering(offeringId, termId, courseId, "teacher-1",
                        "Class A", capacity, enrolledCount, status, 0, null, null), schedules));
    }

    private void seedTerm(String termId, String termCode, String status,
                          Instant enrollmentStart, Instant enrollmentEnd) {
        new TransactionManager(connections).inTransaction(connection -> {
            repository.insertTerm(connection, new Term(termId, termCode, termCode,
                        LocalDate.of(2026, 9, 1), LocalDate.of(2027, 1, 15),
                        enrollmentStart, enrollmentEnd, NOW.plusSeconds(180),
                        NOW.plusSeconds(240), status, 0, null, null));
            if ("ACTIVE".equals(status) && !NOW.isBefore(enrollmentStart) && NOW.isBefore(enrollmentEnd))
                repository.insertSelectionPhase(connection, new SelectionPhase("phase-" + termId, termId,
                        "ENROLLMENT", "Retake selection", "OPEN", 0, null, null));
            return null;
        });
    }

    private void seedActive(String offeringId, String studentId) {
        new TransactionManager(connections).inTransaction(connection -> repository.insertEnrollment(
                connection, new Enrollment(UUID.randomUUID().toString(), offeringId, studentId,
                        "NORMAL", "ACTIVE", NOW.minusSeconds(60), null, 0, null, null)));
    }

    private CourseService createService(CourseAuthorizationGateway authorization,
                                        CourseStudentGateway students,
                                        StripedResourceLockManager locks) {
        CourseStudentGateway checkedStudents = CourseStudentGateway.of(
                students::getEnrollmentEligibility,
                studentId -> studentRecords.values().stream()
                        .anyMatch(record -> record.studentId().equals(studentId)
                                && "ACTIVE".equals(record.status())));
        return new CourseServiceImpl(authorization, checkedStudents, repository, locks,
                new TransactionManager(connections), new TermWindowPolicy(),
                new ScheduleConflictPolicy(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Schedule schedule(String id, String offeringId, DayOfWeek day,
                                     int startPeriod, int endPeriod) {
        return new Schedule(id, offeringId, day, startPeriod, endPeriod, 1, 16, "A101");
    }

    private Offering readOffering(String offeringId) {
        return new TransactionManager(connections).inTransaction(
                connection -> repository.requireOffering(connection, offeringId));
    }

    private long activeCount(String offeringId) {
        try (Connection connection = connections.open();
             var statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM tblEnrollment WHERE offeringId=? AND enrollmentStatus='ACTIVE'")) {
            statement.setString(1, offeringId);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private long attemptCount() {
        try (Connection connection = connections.open();
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT COUNT(*) FROM tblCourseAttempt")) {
            result.next();
            return result.getLong(1);
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private long naturalKeyCount(String studentId, String offeringId) {
        try (Connection connection = connections.open();
             var statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM tblEnrollment WHERE studentId=? AND offeringId=?")) {
            statement.setString(1, studentId);
            statement.setString(2, offeringId);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private static <T> List<Outcome<T>> concurrently(int clients,
                                                      Function<Integer, T> action) throws Exception {
        CountDownLatch ready = new CountDownLatch(clients);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(clients);
        try {
            List<Future<Outcome<T>>> futures = new ArrayList<>();
            for (int index = 0; index < clients; index++) {
                int client = index;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    try {
                        if (!start.await(5, TimeUnit.SECONDS)) {
                            return new Outcome<>(null,
                                    new AssertionError("concurrent start was not released"));
                        }
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                        return new Outcome<>(null, error);
                    }
                    try {
                        return new Outcome<>(action.apply(client), null);
                    } catch (Throwable failure) {
                        return new Outcome<>(null, failure);
                    }
                }));
            }
            assertThat(await(ready, 5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Outcome<T>> results = new ArrayList<>();
            for (Future<Outcome<T>> future : futures) {
                results.add(get(future, 10, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
            assertThat(awaitTermination(pool, 5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static boolean await(CountDownLatch latch, long timeout, TimeUnit unit)
            throws InterruptedException {
        try {
            return latch.await(timeout, unit);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw error;
        }
    }

    private static <T> T get(Future<T> future, long timeout, TimeUnit unit) throws Exception {
        try {
            return future.get(timeout, unit);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw error;
        }
    }

    private static boolean awaitTermination(ExecutorService pool, long timeout, TimeUnit unit)
            throws InterruptedException {
        try {
            return pool.awaitTermination(timeout, unit);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw error;
        }
    }

    private record Outcome<T>(T value, Throwable failure) {
        boolean success() { return failure == null; }
    }

    private static Path schema() {
        return Path.of("..", "vcampus-database", "schema", "030_course.sql");
    }
}
