package edu.seu.vcampus.server.course.service;

import edu.seu.vcampus.common.course.ChangeOfferingCommand;
import edu.seu.vcampus.common.course.EnrollmentView;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.course.domain.CourseRuleException;
import edu.seu.vcampus.server.course.domain.ScheduleConflictPolicy;
import edu.seu.vcampus.server.course.domain.TermWindowPolicy;
import edu.seu.vcampus.server.course.repository.AccessCourseRepository;
import edu.seu.vcampus.server.course.repository.Course;
import edu.seu.vcampus.server.course.repository.CourseRepository;
import edu.seu.vcampus.server.course.repository.Enrollment;
import edu.seu.vcampus.server.course.repository.Offering;
import edu.seu.vcampus.server.course.repository.Term;
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
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrentAdjustmentTest {
    private static final Instant NOW = Instant.parse("2026-08-10T00:00:00Z");
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
        service = new CourseServiceImpl(token -> new CourseSessionIdentity("user", "STUDENT"),
                user -> new StudentEnrollmentEligibility("student", "ACTIVE"), repository,
                new StripedResourceLockManager(), new TransactionManager(connections),
                new TermWindowPolicy(), new ScheduleConflictPolicy(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void concurrentChangesUseSortedOfferingLocksWithoutEnrollmentCountDrift() throws Exception {
        seedCatalog();
        seedOffering("source", "course-source", 1, 1);
        seedOffering("alpha", "course-alpha", 1, 0);
        seedOffering("zulu", "course-zulu", 1, 0);
        Enrollment source = seedActive("source");

        List<Outcome> outcomes = concurrently(List.of(
                new ChangeOfferingCommand(source.enrollmentId(), "zulu", source.rowVersion()),
                new ChangeOfferingCommand(source.enrollmentId(), "alpha", source.rowVersion())));

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded())
                .extracting(outcome -> ((CourseRuleException) outcome.failure()).code())
                .containsOnly("COURSE_ENROLLMENT_NOT_ACTIVE");
        assertThat(activeCount("source")).isZero();
        assertThat(offering("source").enrolledCount()).isZero();
        assertThat(activeCount("alpha") + activeCount("zulu")).isEqualTo(1);
        assertThat(offering("alpha").enrolledCount() + offering("zulu").enrolledCount()).isEqualTo(1);
    }

    private void seedCatalog() {
        inTransaction(c -> {
            repository.insertTerm(c, new Term("term", "2026-1", "Term", LocalDate.of(2026, 9, 1),
                    LocalDate.of(2027, 1, 1), NOW.minusSeconds(3600), NOW.minusSeconds(1800),
                    NOW.minusSeconds(60), NOW.plusSeconds(60), "ACTIVE", 0, null, null));
            for (String id : List.of("source", "alpha", "zulu")) {
                repository.insertCourse(c, new Course("course-" + id, id, id, BigDecimal.ONE,
                        16, null, true, 0, null, null));
            }
            return null;
        });
    }

    private void seedOffering(String id, String courseId, int capacity, int count) {
        inTransaction(c -> repository.insertOffering(c, new Offering(id, "term", courseId, "teacher", id,
                capacity, count, "OPEN", 0, null, null), List.of()));
    }

    private Enrollment seedActive(String offeringId) {
        return inTransaction(c -> repository.insertEnrollment(c, new Enrollment("enrollment", offeringId, "student",
                "NORMAL", "ACTIVE", NOW.minusSeconds(10), null, 0, null, null)));
    }

    private List<Outcome> concurrently(List<ChangeOfferingCommand> commands) throws Exception {
        CountDownLatch ready = new CountDownLatch(commands.size());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(commands.size());
        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (ChangeOfferingCommand command : commands) futures.add(pool.submit(() -> {
                ready.countDown(); start.await();
                try { return new Outcome(service.changeDuringAdjustment("token", command), null); }
                catch (Throwable failure) { return new Outcome(null, failure); }
            }));
            ready.await(); start.countDown();
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : futures) outcomes.add(future.get());
            return outcomes;
        } finally { pool.shutdownNow(); }
    }

    private Offering offering(String id) { return inTransaction(c -> repository.requireOffering(c, id)); }
    private long activeCount(String id) {
        try (Connection c = connections.open(); var s = c.prepareStatement(
                "SELECT COUNT(*) FROM tblEnrollment WHERE offeringId=? AND enrollmentStatus='ACTIVE'")) {
            s.setString(1, id); try (var r = s.executeQuery()) { r.next(); return r.getLong(1); }
        } catch (Exception error) { throw new IllegalStateException(error); }
    }
    private <T> T inTransaction(edu.seu.vcampus.server.persistence.SqlWork<T> work) {
        return new TransactionManager(connections).inTransaction(work);
    }
    private static Path schema() { return Path.of("..", "vcampus-database", "schema", "030_course.sql"); }
    private record Outcome(EnrollmentView result, Throwable failure) { boolean succeeded() { return failure == null; } }
}
