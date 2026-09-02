package edu.seu.vcampus.server.course.service;

import edu.seu.vcampus.common.course.EnrollCommand;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrentEnrollmentTest {
    private static final Instant NOW = Instant.parse("2026-08-10T00:00:00Z");

    private final Map<String, CourseSessionIdentity> sessions = new ConcurrentHashMap<>();
    private final Map<String, StudentEnrollmentEligibility> students = new ConcurrentHashMap<>();
    private CourseService service;
    private CourseRepository repository;
    private ConnectionProvider connections;

    @BeforeEach
    void createServiceWithRealAccessPersistence() throws Exception {
        Path data = Path.of("target", "test-data");
        Files.createDirectories(data);
        String url = "jdbc:ucanaccess://" + data.resolve(UUID.randomUUID() + ".accdb")
                + ";newDatabaseVersion=V2010";
        try (Connection connection = DriverManager.getConnection(url)) {
            for (String statement : Files.readString(schema()).split(";")) {
                if (!statement.isBlank()) {
                    connection.createStatement().execute(statement);
                }
            }
        }
        connections = () -> DriverManager.getConnection(url);
        repository = new AccessCourseRepository();
        service = new CourseServiceImpl(
                sessions::get,
                userId -> students.get(userId),
                repository,
                new StripedResourceLockManager(),
                new TransactionManager(connections),
                new TermWindowPolicy(),
                new ScheduleConflictPolicy(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void exactlyOneStudentWinsTheLastSeat() throws Exception {
        int clients = CourseTestConfig.concurrentClients();
        seedOffering("offering-1", "course-1", 29, 30);
        for (int index = 0; index < clients; index++) {
            registerStudent("token-" + index, "student-" + index);
        }
        StripedResourceLockManager sharedLocks = new StripedResourceLockManager();
        List<CourseService> independentlyConstructedServices = new ArrayList<>();
        for (int index = 0; index < clients; index++) {
            independentlyConstructedServices.add(createService(sharedLocks));
        }

        List<Outcome<EnrollmentView>> outcomes = concurrently(clients,
                index -> independentlyConstructedServices.get(index)
                        .enroll("token-" + index, new EnrollCommand("offering-1")));

        assertThat(outcomes.stream().filter(Outcome::isSuccess)).hasSize(1);
        assertThat(activeCount("offering-1")).isEqualTo(30);
        assertThat(offering("offering-1").enrolledCount()).isEqualTo(30);
        assertThat(outcomes.stream().filter(outcome -> !outcome.isSuccess()))
                .extracting(outcome -> ((CourseRuleException) outcome.failure()).code())
                .containsOnly("COURSE_OFFERING_FULL");
    }

    @Test
    void sameStudentConcurrentDuplicateHasExactlyOneSuccess() throws Exception {
        int clients = CourseTestConfig.concurrentClients();
        seedOffering("offering-1", "course-1", 0, 30);
        registerStudent("same-token", "same-student");

        List<Outcome<EnrollmentView>> outcomes = concurrently(clients,
                ignored -> service.enroll("same-token", new EnrollCommand("offering-1")));

        assertThat(outcomes.stream().filter(Outcome::isSuccess)).hasSize(1);
        assertThat(activeCount("offering-1")).isEqualTo(1);
        assertThat(offering("offering-1").enrolledCount()).isEqualTo(1);
        assertThat(outcomes.stream().filter(outcome -> !outcome.isSuccess()))
                .extracting(outcome -> ((CourseRuleException) outcome.failure()).code())
                .containsOnly("COURSE_DUPLICATE_ENROLLMENT");
    }

    private void seedOffering(String offeringId, String courseId, int enrolledCount, int capacity) {
        try (Connection connection = connections.open()) {
            repository.insertTerm(connection, new Term("term-1", "2026-2027-1", "Autumn",
                    LocalDate.of(2026, 9, 1), LocalDate.of(2027, 1, 15),
                    NOW.minusSeconds(3600), NOW.plusSeconds(3600),
                    NOW.plusSeconds(7200), NOW.plusSeconds(10800), "PLANNED", 0, null, null));
            repository.insertCourse(connection, new Course(courseId, "CS101", "Programming",
                    BigDecimal.valueOf(3), 48, null, true, 0, null, null));
            repository.insertOffering(connection, new Offering(offeringId, "term-1", courseId,
                    "teacher-1", "Class A", capacity, enrolledCount, "OPEN", 0, null, null), List.of());
            for (int index = 0; index < enrolledCount; index++) {
                repository.insertEnrollment(connection, new Enrollment(UUID.randomUUID().toString(), offeringId,
                        "existing-student-" + index, "NORMAL", "ACTIVE", NOW, null, 0, null, null));
            }
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private void registerStudent(String token, String studentId) {
        String userId = "user-" + studentId;
        sessions.put(token, new CourseSessionIdentity(userId, "STUDENT"));
        students.put(userId, new StudentEnrollmentEligibility(studentId, "ACTIVE"));
    }

    private CourseService createService(StripedResourceLockManager locks) {
        return new CourseServiceImpl(
                sessions::get,
                userId -> students.get(userId),
                repository,
                locks,
                new TransactionManager(connections),
                new TermWindowPolicy(),
                new ScheduleConflictPolicy(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Offering offering(String offeringId) {
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

    private static <T> List<Outcome<T>> concurrently(int clients, Function<Integer, T> action) throws Exception {
        CountDownLatch ready = new CountDownLatch(clients);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(clients);
        try {
            List<Future<Outcome<T>>> futures = new ArrayList<>();
            for (int index = 0; index < clients; index++) {
                int client = index;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        return Outcome.success(action.apply(client));
                    } catch (Throwable failure) {
                        return Outcome.failure(failure);
                    }
                }));
            }
            ready.await();
            start.countDown();
            List<Outcome<T>> results = new ArrayList<>();
            for (Future<Outcome<T>> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private static Path schema() {
        return Path.of("..", "vcampus-database", "schema", "030_course.sql");
    }

    private record Outcome<T>(T value, Throwable failure) {
        static <T> Outcome<T> success(T value) {
            return new Outcome<>(value, null);
        }

        static <T> Outcome<T> failure(Throwable failure) {
            return new Outcome<>(null, failure);
        }

        boolean isSuccess() {
            return failure == null;
        }
    }
}

final class CourseTestConfig {
    private CourseTestConfig() {
    }

    static int concurrentClients() {
        return Integer.getInteger("course.test.concurrentClients", 20);
    }
}
