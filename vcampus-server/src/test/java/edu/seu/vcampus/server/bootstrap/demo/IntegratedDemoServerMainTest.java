package edu.seu.vcampus.server.bootstrap.demo;

import edu.seu.vcampus.common.course.EnrollCommand;
import edu.seu.vcampus.common.course.EnrollmentView;
import edu.seu.vcampus.common.course.OfferingSearchQuery;
import edu.seu.vcampus.common.course.OfferingSummary;
import edu.seu.vcampus.common.course.ScheduleItem;
import edu.seu.vcampus.common.course.TermView;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.protocol.EmptyRequest;
import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.MessageType;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.user.LoginCommand;
import edu.seu.vcampus.common.user.LoginResult;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.server.bootstrap.ApplicationRuntime;
import edu.seu.vcampus.server.routing.ClientContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntegratedDemoServerMainTest {
    private static final ClientContext CONTEXT = new ClientContext("demo-test", "127.0.0.1");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-31T12:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path temporaryDirectory;

    @Test
    void preparesIdempotentAuthenticatedThreeRoleDemoData() throws Exception {
        Path database = temporaryDirectory.resolve("course-user-demo.accdb");
        ApplicationRuntime first = IntegratedDemoServerMain.prepare(
                database, databaseRoot(), CLOCK);

        LoginResult administrator = login(first, "ADMIN", "Admin1234");
        assertThat(administrator.user().role()).isEqualTo(UserRole.ADMIN);
        assertThat(administrator.mustChangePassword()).isTrue();

        LoginResult student = login(first, "213000001", "Student1234");
        assertThat(student.user().userId()).isEqualTo("213000001");
        assertThat(student.user().role()).isEqualTo(UserRole.STUDENT);
        assertThat(student.mustChangePassword()).isFalse();

        LoginResult teacher = login(first, "TEACHER_DEMO", "Teacher1234");
        assertThat(teacher.user().userId()).isEqualTo("teacher-demo-001");
        assertThat(teacher.user().role()).isEqualTo(UserRole.TEACHER);

        List<TermView> terms = data(route(first, "COURSE_TERM_LIST",
                student.sessionToken(), EmptyRequest.INSTANCE));
        assertThat(terms).hasSize(1);
        PageResult<OfferingSummary> offerings = data(route(first, "COURSE_SEARCH_OFFERINGS",
                student.sessionToken(), new OfferingSearchQuery(
                        terms.getFirst().termId(), "MATH101", null, false, 0, 20)));
        assertThat(offerings.items()).hasSize(1);

        EnrollmentView enrollment = data(route(first, "COURSE_ENROLL", student.sessionToken(),
                new EnrollCommand(offerings.items().getFirst().offeringId())));
        assertThat(enrollment.studentId()).isEqualTo("213000001");
        List<ScheduleItem> schedule = data(route(first, "COURSE_GET_MY_SCHEDULE",
                teacher.sessionToken(), EmptyRequest.INSTANCE));
        assertThat(schedule).extracting(ScheduleItem::offeringId)
                .containsExactly(offerings.items().getFirst().offeringId());

        ApplicationRuntime second = IntegratedDemoServerMain.prepare(
                database, databaseRoot(), CLOCK);
        LoginResult secondStudent = login(second, "213000001", "Student1234");
        List<TermView> repeatedTerms = data(route(second, "COURSE_TERM_LIST",
                secondStudent.sessionToken(), EmptyRequest.INSTANCE));
        assertThat(repeatedTerms).hasSize(1);
    }

    @Test
    void refusesToSeedAnythingExceptTheDedicatedDemoDatabase() {
        Path productionDatabase = temporaryDirectory.resolve("vCampus.accdb");

        assertThatThrownBy(() -> IntegratedDemoServerMain.prepare(
                productionDatabase, databaseRoot(), CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("course-user-demo.accdb");
        assertThat(productionDatabase).doesNotExist();
    }

    private static LoginResult login(ApplicationRuntime runtime, String loginId, String password) {
        return data(route(runtime, "USER_LOGIN", null,
                new LoginCommand(loginId, password.toCharArray(), "integrated-demo-test")));
    }

    private static ResponseBody<?> route(ApplicationRuntime runtime, String command,
            String token, Serializable body) {
        return runtime.router().route(new Message(UUID.randomUUID().toString(),
                MessageType.REQUEST, command, token, body, 0), CONTEXT);
    }

    @SuppressWarnings("unchecked")
    private static <T> T data(ResponseBody<?> response) {
        assertThat(response.success()).withFailMessage("code=%s", response.code()).isTrue();
        return (T) response.data();
    }

    private static Path databaseRoot() {
        Path root = Path.of("vcampus-database");
        return Files.exists(root) ? root : Path.of("..", "vcampus-database");
    }
}
