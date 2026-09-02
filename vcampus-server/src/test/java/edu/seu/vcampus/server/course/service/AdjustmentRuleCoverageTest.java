package edu.seu.vcampus.server.course.service;

import edu.seu.vcampus.common.course.ChangeOfferingCommand;
import edu.seu.vcampus.common.course.DropCommand;
import edu.seu.vcampus.common.course.LateAddCommand;
import edu.seu.vcampus.common.course.EnrollmentView;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.course.domain.AdjustmentClosedException;
import edu.seu.vcampus.server.course.domain.DropClosedException;
import edu.seu.vcampus.server.course.domain.EnrollmentClosedException;
import edu.seu.vcampus.server.course.domain.EnrollmentVersionMismatchException;
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
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdjustmentRuleCoverageTest {
    private static final Instant NOW = Instant.parse("2026-08-10T00:00:00Z");
    private CourseRepository repository;
    private ConnectionProvider connections;

    @BeforeEach void setUp() throws Exception {
        Path data = Path.of("target", "test-data"); Files.createDirectories(data);
        String url = "jdbc:ucanaccess://" + data.resolve(UUID.randomUUID() + ".accdb") + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true";
        try (Connection c = DriverManager.getConnection(url)) { for (String s : Files.readString(schema()).split(";")) if (!s.isBlank()) c.createStatement().execute(s); }
        connections = () -> DriverManager.getConnection(url); repository = new AccessCourseRepository();
    }

    @Test void closedTermRejectsAddDropAndChangeWithoutMutationsAndAuditsEach() {
        seed("CLOSED", NOW.minusSeconds(60), NOW.plusSeconds(60)); offer("source", "course-1", 2, 1, "OPEN", List.of()); offer("target", "course-2", 2, 0, "OPEN", List.of());
        Enrollment source = active("source", "student"); CourseService service = service(repository);
        assertThatThrownBy(() -> service.addDuringAdjustment("token", new LateAddCommand("target"))).isInstanceOf(AdjustmentClosedException.class);
        assertThatThrownBy(() -> service.drop("token", new DropCommand(source.enrollmentId(), 0))).isInstanceOf(DropClosedException.class);
        assertThatThrownBy(() -> service.changeDuringAdjustment("token", new ChangeOfferingCommand(source.enrollmentId(), "target", 0))).isInstanceOf(AdjustmentClosedException.class);
        assertThat(enrollment(source.enrollmentId()).enrollmentStatus()).isEqualTo("ACTIVE"); assertThat(count("target")).isZero();
        assertThat(audits()).hasSize(3).allSatisfy(a -> assertThat(a.operationResult()).isEqualTo("FAILED"));
        assertThat(audits()).filteredOn(a -> "DROP".equals(a.adjustmentType())).singleElement()
                .extracting(EnrollmentAdjustment::failureCode).isEqualTo("COURSE_DROP_NOT_OPEN");
        assertThat(audits()).filteredOn(a -> !"DROP".equals(a.adjustmentType())).allSatisfy(a ->
                assertThat(a.failureCode()).isEqualTo("COURSE_ADJUSTMENT_NOT_OPEN"));
    }

    @Test void outsideWindowRejectsAddDropAndChangeWithoutMutationsAndAuditsEach() {
        seed("ACTIVE", NOW.plusSeconds(1), NOW.plusSeconds(60)); offer("source", "course-1", 2, 1, "OPEN", List.of()); offer("target", "course-2", 2, 0, "OPEN", List.of());
        Enrollment source = active("source", "student"); CourseService service = service(repository);
        assertThatThrownBy(() -> service.addDuringAdjustment("token", new LateAddCommand("target"))).isInstanceOf(AdjustmentClosedException.class);
        assertThatThrownBy(() -> service.drop("token", new DropCommand(source.enrollmentId(), 0))).isInstanceOf(DropClosedException.class);
        assertThatThrownBy(() -> service.changeDuringAdjustment("token", new ChangeOfferingCommand(source.enrollmentId(), "target", 0))).isInstanceOf(AdjustmentClosedException.class);
        assertThat(enrollment(source.enrollmentId()).enrollmentStatus()).isEqualTo("ACTIVE"); assertThat(audits()).hasSize(3);
        assertThat(audits()).filteredOn(a -> "DROP".equals(a.adjustmentType())).singleElement()
                .extracting(EnrollmentAdjustment::failureCode).isEqualTo("COURSE_DROP_NOT_OPEN");
    }

    @Test void lateAddRejectsFullTargetAndAuditsFailure() {
        seedOpen(); offer("full", "course-1", 1, 1, "OPEN", List.of()); active("full", "other");
        assertThatThrownBy(() -> service(repository).addDuringAdjustment("token", new LateAddCommand("full"))).isInstanceOf(OfferingFullException.class);
        assertThat(count("full")).isEqualTo(1); assertThat(audits()).singleElement().extracting(EnrollmentAdjustment::failureCode).isEqualTo("COURSE_OFFERING_FULL");
    }

    @Test void lateAddRejectsSameCourseAndScheduleConflictAndClosedOffering() {
        seedOpen(); offer("selected", "course-1", 3, 1, "OPEN", List.of(schedule("s", "selected", DayOfWeek.MONDAY))); active("selected", "student");
        offer("duplicate", "course-1", 3, 0, "OPEN", List.of()); offer("conflict", "course-2", 3, 0, "OPEN", List.of(schedule("c", "conflict", DayOfWeek.MONDAY))); offer("closed", "course-2", 3, 0, "CLOSED", List.of());
        CourseService service = service(repository);
        assertThatThrownBy(() -> service.addDuringAdjustment("token", new LateAddCommand("duplicate"))).hasMessageContaining("COURSE_DUPLICATE_ENROLLMENT");
        assertThatThrownBy(() -> service.addDuringAdjustment("token", new LateAddCommand("conflict"))).hasMessageContaining("COURSE_SCHEDULE_CONFLICT");
        assertThatThrownBy(() -> service.addDuringAdjustment("token", new LateAddCommand("closed"))).isInstanceOf(EnrollmentClosedException.class);
        assertThat(audits()).hasSize(3); assertThat(count("duplicate") + count("conflict") + count("closed")).isZero();
    }

    @Test void staleDropAndChangeVersionsRollbackAndAuditFailure() {
        seedOpen(); offer("source", "course-1", 3, 1, "OPEN", List.of()); offer("target", "course-2", 3, 0, "OPEN", List.of()); Enrollment source = active("source", "student");
        CourseService service = service(repository);
        assertThatThrownBy(() -> service.drop("token", new DropCommand(source.enrollmentId(), 1))).isInstanceOf(EnrollmentVersionMismatchException.class);
        assertThatThrownBy(() -> service.changeDuringAdjustment("token", new ChangeOfferingCommand(source.enrollmentId(), "target", 1))).isInstanceOf(EnrollmentVersionMismatchException.class);
        assertThat(enrollment(source.enrollmentId()).enrollmentStatus()).isEqualTo("ACTIVE"); assertThat(count("source")).isEqualTo(1); assertThat(count("target")).isZero();
        assertThat(audits()).hasSize(2).allSatisfy(a -> assertThat(a.failureCode()).isEqualTo("COMMON_CONCURRENT_MODIFICATION"));
    }

    @Test void sameCourseChangeExcludesSourceAndDroppedTargetReactivatesItsStableRecord() {
        seedOpen(); offer("source", "course-1", 3, 1, "OPEN", List.of()); offer("target", "course-1", 3, 0, "OPEN", List.of()); Enrollment source = active("source", "student");
        Enrollment retained = inTx(c -> repository.insertEnrollment(c, new Enrollment("retained", "target", "student", "LATE_ADD", "DROPPED", NOW.minusSeconds(8), NOW.minusSeconds(4), 0, null, null)));
        EnrollmentView changed = service(repository).changeDuringAdjustment("token", new ChangeOfferingCommand(source.enrollmentId(), "target", 0));
        assertThat(changed.enrollmentId()).isEqualTo(retained.enrollmentId()); assertThat(changed.enrollmentStatus()).isEqualTo("ACTIVE"); assertThat(enrollment(source.enrollmentId()).enrollmentStatus()).isEqualTo("DROPPED");
        assertThat(count("source")).isZero(); assertThat(count("target")).isEqualTo(1);
    }

    @Test void successAuditWriteFailureRollsBackLateAddAndCount() {
        seedOpen(); offer("target", "course-1", 3, 0, "OPEN", List.of());
        assertThatThrownBy(() -> service(failSuccessAudit(repository)).addDuringAdjustment("token", new LateAddCommand("target"))).isInstanceOf(IllegalStateException.class).hasMessage("forced success-audit write");
        assertThat(count("target")).isZero(); assertThat(offering("target").enrolledCount()).isZero();
    }

    private CourseService service(CourseRepository r) { return new CourseServiceImpl(t -> new CourseSessionIdentity("user", "STUDENT"), u -> new StudentEnrollmentEligibility("student", "ACTIVE"), r, new StripedResourceLockManager(), new TransactionManager(connections), new TermWindowPolicy(), new ScheduleConflictPolicy(), Clock.fixed(NOW, ZoneOffset.UTC)); }
    private void seedOpen() { seed("ACTIVE", NOW.minusSeconds(60), NOW.plusSeconds(60)); }
    private void seed(String status, Instant start, Instant end) { inTx(c -> { repository.insertTerm(c, new Term("term", "2026-1", "Term", LocalDate.of(2026,9,1), LocalDate.of(2027,1,1), NOW.minusSeconds(3600), NOW.minusSeconds(1800), start, end, status, 0, null, null)); repository.insertCourse(c, new Course("course-1", "CS101", "One", BigDecimal.ONE, 16, null, true, 0, null, null)); repository.insertCourse(c, new Course("course-2", "CS102", "Two", BigDecimal.ONE, 16, null, true, 0, null, null)); return null; }); }
    private void offer(String id, String course, int capacity, int enrolled, String status, List<Schedule> schedules) { inTx(c -> repository.insertOffering(c, new Offering(id, "term", course, "teacher", id, capacity, enrolled, status, 0, null, null), schedules)); }
    private Enrollment active(String offering, String student) { return inTx(c -> repository.insertEnrollment(c, new Enrollment(UUID.randomUUID().toString(), offering, student, "NORMAL", "ACTIVE", NOW.minusSeconds(3), null, 0, null, null))); }
    private static Schedule schedule(String id, String offering, DayOfWeek day) { return new Schedule(id, offering, day, 1, 2, 1, 16, "A"); }
    private CourseRepository failSuccessAudit(CourseRepository delegate) { return (CourseRepository) Proxy.newProxyInstance(CourseRepository.class.getClassLoader(), new Class<?>[]{CourseRepository.class}, (p, m, a) -> { if ("insertAdjustment".equals(m.getName()) && "SUCCEEDED".equals(((EnrollmentAdjustment) a[1]).operationResult())) throw new IllegalStateException("forced success-audit write"); try { return m.invoke(delegate, a); } catch (InvocationTargetException e) { throw e.getCause(); } }); }
    private Enrollment enrollment(String id) { return inTx(c -> repository.requireEnrollment(c, id)); }
    private Offering offering(String id) { return inTx(c -> repository.requireOffering(c, id)); }
    private int count(String id) { try (Connection c = connections.open(); var s = c.prepareStatement("SELECT COUNT(*) FROM tblEnrollment WHERE offeringId=? AND enrollmentStatus='ACTIVE'")) { s.setString(1,id); try(var r=s.executeQuery()){r.next();return r.getInt(1);} } catch(Exception e){throw new IllegalStateException(e);} }
    private List<EnrollmentAdjustment> audits() { return inTx(c -> repository.findAdjustmentsByStudent(c, "student")); }
    private <T> T inTx(edu.seu.vcampus.server.persistence.SqlWork<T> work) { return new TransactionManager(connections).inTransaction(work); }
    private static Path schema() { return Path.of("..", "vcampus-database", "schema", "030_course.sql"); }
}
