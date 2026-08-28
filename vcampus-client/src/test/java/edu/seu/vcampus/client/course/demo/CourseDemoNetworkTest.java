package edu.seu.vcampus.client.course.demo;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.course.service.CourseClientService;
import edu.seu.vcampus.common.course.EnrollCommand;
import edu.seu.vcampus.common.course.OfferingSearchQuery;
import edu.seu.vcampus.server.course.demo.CourseDemoServerMain;
import edu.seu.vcampus.server.network.SocketServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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

            var enrollment = step("enroll", () -> client.enroll(
                    new EnrollCommand(offerings.getFirst().offeringId())).join());

            assertThat(offerings).hasSize(2);
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

    private static <T> T step(String name, Supplier<T> action) {
        try { return action.get(); }
        catch (RuntimeException failure) { throw new AssertionError("Demo network step failed: " + name, failure); }
    }

    private static Path schema() {
        Path direct = Path.of("vcampus-database", "schema", "030_course.sql");
        return Files.exists(direct) ? direct : Path.of("..", "vcampus-database", "schema", "030_course.sql");
    }
}
