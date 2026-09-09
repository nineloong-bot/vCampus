package edu.seu.vcampus.client.course.demo;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.course.service.CourseClientException;
import edu.seu.vcampus.client.course.service.CourseClientService;
import edu.seu.vcampus.common.course.EnrollCommand;
import edu.seu.vcampus.common.course.CourseCatalogQuery;
import edu.seu.vcampus.common.course.CourseSelectionQuery;
import edu.seu.vcampus.common.course.CreateOfferingCommand;
import edu.seu.vcampus.common.course.OfferingSearchQuery;
import edu.seu.vcampus.server.course.demo.CourseDemoServerMain;
import edu.seu.vcampus.server.network.SocketServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class CourseDemoNetworkTest {
    @TempDir Path directory;

    @Test
    void realClientAndSocketServerCompleteTheStudentDemoFlow() throws Exception {
        var runtime = CourseDemoServerMain.prepare(directory.resolve("course-demo.accdb"), schema(), "ENROLLMENT");
        SocketServer server = new SocketServer(0, 4, 20, runtime.router());
        Thread serving = Thread.ofPlatform().start(() -> {
            try { server.serve(); } catch (Exception ignored) { }
        });
        try (ClientConnection connection = new ClientConnection("127.0.0.1", server.localPort())) {
            connection.connect(Duration.ofSeconds(5));
            connection.setSessionToken("student-demo-1");
            CourseClientService client = new CourseClientService(connection);
            var term = step("list terms", () -> client.listTerms().join().getFirst());
            var offerings = step("search offerings", () -> client.searchOfferings(
                    new OfferingSearchQuery(term.termId(), "", null, true, 0, 20)).join().items());
            var selectable = step("search student courses", () -> client.searchStudentCourses(
                            new CourseSelectionQuery(term.termId(), "B09G0011", null, 0, 20)).join().items())
                    .getFirst().teachingClasses().getFirst().offering();

            var enrollment = step("enroll", () -> client.enroll(
                    new EnrollCommand(selectable.offeringId())).join());

            assertThat(offerings).extracting(offering -> offering.courseCode())
                    .contains("B09D0012", "B09G0011", "BJSL0061")
                    .doesNotContain("MATH101", "CS201", "DEMO-RACE");
            assertThat(enrollment.studentId()).isEqualTo("student-demo-1");
            assertThat(step("current enrollments", () -> client.getCurrentEnrollments().join())).hasSize(1);
            assertThat(step("current schedule", () -> client.getCurrentSchedule().join())).hasSize(1);
        } finally {
            server.stopAccepting();
            server.awaitRequests(Duration.ofSeconds(5));
            server.close();
            serving.join(5_000);
        }
    }

    @Test
    void twoIndependentClientsCanRaceForTheDemoLastSeat() throws Exception {
        var runtime = CourseDemoServerMain.prepare(directory.resolve("course-race.accdb"), schema(), "ENROLLMENT");
        var term = runtime.service().listTerms().getFirst();
        var course = runtime.service().searchCatalog(new CourseCatalogQuery("B09G0011", true, 0, 10))
                .items().getFirst();
        var raceOffering = runtime.service().createOffering(new CreateOfferingCommand(term.termId(),
                course.courseId(), "teacher-user", "并发名额验证班", 1, 1, "OPEN", List.of()));
        SocketServer server = new SocketServer(0, 4, 20, runtime.router());
        Thread serving = Thread.ofPlatform().start(() -> {
            try { server.serve(); } catch (Exception ignored) { }
        });
        try (ClientConnection firstConnection = new ClientConnection("127.0.0.1", server.localPort());
             ClientConnection secondConnection = new ClientConnection("127.0.0.1", server.localPort())) {
            firstConnection.connect(Duration.ofSeconds(5));
            secondConnection.connect(Duration.ofSeconds(5));
            firstConnection.setSessionToken("student-demo-1");
            secondConnection.setSessionToken("student-demo-2");
            CourseClientService first = new CourseClientService(firstConnection);
            CourseClientService second = new CourseClientService(secondConnection);
            CompletableFuture<RaceResult> firstAttempt = CompletableFuture.supplyAsync(
                    () -> attempt(first, raceOffering.offeringId()));
            CompletableFuture<RaceResult> secondAttempt = CompletableFuture.supplyAsync(
                    () -> attempt(second, raceOffering.offeringId()));
            List<RaceResult> results = List.of(firstAttempt.join(), secondAttempt.join());

            assertThat(results).as("race results: %s", results)
                    .filteredOn(RaceResult::success).hasSize(1);
            assertThat(results).filteredOn(result -> !result.success())
                    .singleElement().extracting(RaceResult::code).isEqualTo("COURSE_OFFERING_FULL");
        } finally {
            server.stopAccepting();
            server.awaitRequests(Duration.ofSeconds(5));
            server.close();
            serving.join(5_000);
        }
    }

    private static RaceResult attempt(CourseClientService client, String offeringId) {
        try {
            client.enroll(new EnrollCommand(offeringId)).join();
            return new RaceResult(true, "SUCCESS");
        } catch (CompletionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof CourseClientException clientFailure) {
                return new RaceResult(false, clientFailure.code());
            }
            throw failure;
        }
    }

    private static <T> T step(String name, Supplier<T> action) {
        try { return action.get(); }
        catch (RuntimeException failure) { throw new AssertionError("Demo network step failed: " + name, failure); }
    }

    private static Path schema() {
        Path direct = Path.of("vcampus-database", "schema", "030_course.sql");
        return Files.exists(direct) ? direct : Path.of("..", "vcampus-database", "schema", "030_course.sql");
    }

    private record RaceResult(boolean success, String code) { }
}
