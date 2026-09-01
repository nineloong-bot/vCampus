package edu.seu.vcampus.client.integration;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.course.service.CourseClientException;
import edu.seu.vcampus.client.course.service.CourseClientService;
import edu.seu.vcampus.client.course.ui.CourseUiComposition;
import edu.seu.vcampus.client.course.ui.CourseWorkspacePanel;
import edu.seu.vcampus.client.user.service.UserClientService;
import edu.seu.vcampus.common.course.CreateCourseCommand;
import edu.seu.vcampus.common.course.CreateOfferingCommand;
import edu.seu.vcampus.common.course.CreateTermCommand;
import edu.seu.vcampus.common.course.EnrollCommand;
import edu.seu.vcampus.common.course.OfferingSearchQuery;
import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.common.user.LoginResult;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.common.user.UpdateUserRoleCommand;
import edu.seu.vcampus.common.user.UserSearchQuery;
import edu.seu.vcampus.server.bootstrap.ApplicationRuntime;
import edu.seu.vcampus.server.network.SocketServer;
import edu.seu.vcampus.server.persistence.ConnectionProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class LoginCourseSocketIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-08-31T08:00:00Z");
    private static final String PASSWORD = "Password7";
    private static final String STUDENT_USER_ID = "student-user-001";
    private static final String TEACHER_USER_ID = "teacher-user-001";

    @TempDir
    Path temporaryDirectory;

    private SocketServer server;
    private ExecutorService serverThread;
    private Future<?> serving;
    private ClientConnection connection;
    private UserClientService users;
    private CourseClientService courses;

    @BeforeEach
    void startProductionApplicationOnARealSocketAndAccessDatabase() throws Exception {
        Path database = temporaryDirectory.resolve("integration.accdb");
        ConnectionProvider connections = () -> DriverManager.getConnection(
                "jdbc:ucanaccess://" + database + ";newDatabaseVersion=V2010");
        ApplicationRuntime runtime = ApplicationRuntime.create(
                connections, databaseRoot(), Clock.fixed(NOW, ZoneOffset.UTC));
        insertUser(connections, STUDENT_USER_ID, "STUDENT1", UserRole.STUDENT, false);
        insertUser(connections, TEACHER_USER_ID, "TEACHER1", UserRole.TEACHER, false);
        insertUser(connections, "admin-user-001", "ADMIN1", UserRole.ADMIN, false);
        insertUser(connections, "admin-user-002", "ADMIN2", UserRole.ADMIN, false);
        insertUser(connections, "restricted-user-001", "RESTRICTED1", UserRole.STUDENT, true);

        server = new SocketServer(0, 4, 20, runtime.router());
        serverThread = Executors.newSingleThreadExecutor();
        serving = serverThread.submit(() -> {
            server.serve();
            return null;
        });

        connection = new ClientConnection("127.0.0.1", server.localPort());
        connection.connect(Duration.ofSeconds(5));
        users = new UserClientService(connection, "course-user-integration", Duration.ofSeconds(10));
        courses = new CourseClientService(connection);
    }

    @AfterEach
    void stopProductionSocketRuntime() throws Exception {
        if (connection != null) {
            connection.close();
        }
        if (server != null) {
            server.stopAccepting();
            assertThat(server.awaitRequests(Duration.ofSeconds(5))).isTrue();
        }
        if (serving != null) {
            serving.get();
        }
        if (serverThread != null) {
            serverThread.shutdownNow();
        }
    }

    @Test
    void authenticatesEveryCourseRoleAndPreservesSessionSecurityAcrossTheProductionBoundary() {
        LoginResult administrator = login("ADMIN1");
        assertThat(administrator.user().role()).isEqualTo(UserRole.ADMIN);
        assertWorkspaceTabs(administrator, List.of(
                "学期管理", "课程目录", "教学班管理", "修读结果导入", "选退记录"));
        var term = courses.createTerm(term()).join();
        var course = courses.createCourse(new CreateCourseCommand(
                "CS-E2E", "端到端系统测试", BigDecimal.valueOf(3), 48, null, true)).join();
        var offering = courses.createOffering(new CreateOfferingCommand(
                term.termId(), course.courseId(), TEACHER_USER_ID, "E2E-01", 20, "OPEN",
                List.of(new CreateOfferingCommand.ScheduleInput(
                        "MONDAY", 1, 2, 1, 16, "TEST-101")))).join();
        users.logout().join();

        LoginResult student = login("STUDENT1");
        assertThat(student.user().userId()).isEqualTo(STUDENT_USER_ID);
        assertWorkspaceTabs(student, List.of(
                "教学班查询", "我的选课", "我的课表", "退改补", "重修"));
        assertThat(courses.listTerms().join()).extracting("termId").contains(term.termId());
        assertThat(courses.searchOfferings(new OfferingSearchQuery(
                term.termId(), "CS-E2E", null, false, 0, 20)).join().items())
                .extracting("offeringId").containsExactly(offering.offeringId());
        assertThat(courses.enroll(new EnrollCommand(offering.offeringId())).join().studentId())
                .as("temporary active-STUDENT mapping keeps studentId equal to userId")
                .isEqualTo(STUDENT_USER_ID);
        assertCourseFailure(() -> courses.createTerm(term()).join(), "COMMON_FORBIDDEN");

        users.logout().join();
        assertCourseFailure(() -> courses.listTerms().join(), "AUTH_SESSION_EXPIRED");

        LoginResult restricted = login("RESTRICTED1");
        assertThat(restricted.mustChangePassword()).isTrue();
        assertCourseFailure(() -> courses.listTerms().join(),
                "AUTH_INITIAL_PASSWORD_CHANGE_REQUIRED");
        users.logout().join();

        LoginResult teacher = login("TEACHER1");
        assertThat(teacher.user().role()).isEqualTo(UserRole.TEACHER);
        assertWorkspaceTabs(teacher, List.of("教学班查询", "教师课表"));
        assertThat(courses.searchOfferings(new OfferingSearchQuery(
                term.termId(), null, null, false, 0, 20)).join().items())
                .extracting("offeringId").contains(offering.offeringId());
        assertThat(courses.getCurrentSchedule().join())
                .extracting("offeringId").containsExactly(offering.offeringId());
    }

    @Test
    void demotingAnAdministratorExpiresEveryOldTokenForUserAndCourseAdminCommands() throws Exception {
        ClientConnection targetConnection = new ClientConnection("127.0.0.1", server.localPort());
        targetConnection.connect(Duration.ofSeconds(5));
        try {
            UserClientService targetUsers = new UserClientService(
                    targetConnection, "demoted-admin", Duration.ofSeconds(10));
            CourseClientService targetCourses = new CourseClientService(targetConnection);
            LoginResult target = targetUsers.login("ADMIN2", PASSWORD.toCharArray()).join();
            assertThat(target.user().role()).isEqualTo(UserRole.ADMIN);

            login("ADMIN1");
            var update = connection.send("USER_UPDATE_ROLE", new UpdateUserRoleCommand(
                    "admin-user-002", UserRole.TEACHER, target.user().rowVersion() + 1),
                    Duration.ofSeconds(10)).join();
            assertThat(update.success()).withFailMessage("role update failed: %s", update.code()).isTrue();

            var userAdminResponse = targetConnection.send("USER_SEARCH",
                    new UserSearchQuery(null, null, null, 0, 10), Duration.ofSeconds(10)).join();
            assertThat(userAdminResponse.success()).isFalse();
            assertThat(userAdminResponse.code()).isEqualTo("AUTH_SESSION_EXPIRED");
            assertCourseFailure(() -> targetCourses.createTerm(term()).join(),
                    "AUTH_SESSION_EXPIRED");
        } finally {
            targetConnection.close();
        }
    }

    private LoginResult login(String loginId) {
        char[] password = PASSWORD.toCharArray();
        LoginResult result = users.login(loginId, password).join();
        assertThat(password).containsOnly('\0');
        return result;
    }

    private void assertWorkspaceTabs(LoginResult result, List<String> expected) {
        AtomicReference<CourseWorkspacePanel> workspace = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> workspace.set(
                    new CourseUiComposition(courses).workspaceFor(result.user().role())));
        } catch (Exception error) {
            throw new AssertionError(error);
        }
        JTabbedPane tabs = descendants(workspace.get()).stream()
                .filter(JTabbedPane.class::isInstance).map(JTabbedPane.class::cast)
                .findFirst().orElseThrow();
        assertThat(IntStream.range(0, tabs.getTabCount()).mapToObj(tabs::getTitleAt))
                .containsExactlyElementsOf(expected);
        assertThat(workspace.get().getName()).isEqualTo("page.course");
    }

    private static List<Component> descendants(Container root) {
        java.util.ArrayList<Component> found = new java.util.ArrayList<>();
        for (Component child : root.getComponents()) {
            found.add(child);
            if (child instanceof Container nested) found.addAll(descendants(nested));
        }
        return found;
    }

    private static void assertCourseFailure(Runnable action, String code) {
        Throwable failure = catchThrowable(action::run);
        assertThat(failure).isNotNull().hasRootCauseInstanceOf(CourseClientException.class);
        Throwable root = failure;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        assertThat(root).isInstanceOfSatisfying(CourseClientException.class,
                courseFailure -> assertThat(courseFailure.code()).isEqualTo(code));
    }

    private static CreateTermCommand term() {
        return new CreateTermCommand("2026-E2E", "端到端测试学期",
                LocalDate.of(2026, 8, 1), LocalDate.of(2027, 1, 31),
                NOW.minus(Duration.ofDays(7)), NOW.plus(Duration.ofDays(7)),
                NOW.plus(Duration.ofDays(8)), NOW.plus(Duration.ofDays(14)), "ACTIVE");
    }

    private static void insertUser(ConnectionProvider connections, String userId, String loginId,
                                   UserRole role, boolean restricted) throws Exception {
        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        byte[] salt = ("salt-" + loginId).getBytes(StandardCharsets.UTF_8);
        PBEKeySpec specification = new PBEKeySpec(PASSWORD.toCharArray(), salt, 120_000, 256);
        byte[] derived;
        try {
            derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(specification).getEncoded();
        } finally {
            specification.clearPassword();
        }
        try (var connection = connections.open(); var statement = connection.prepareStatement("""
                INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations,
                    roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil,
                    lastLoginAt, rowVersion, createdAt, updatedAt)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, userId);
            statement.setString(2, loginId);
            statement.setString(3, Base64.getEncoder().encodeToString(derived));
            statement.setString(4, Base64.getEncoder().encodeToString(salt));
            statement.setInt(5, 120_000);
            statement.setString(6, role.name());
            statement.setString(7, AccountStatus.ACTIVE.name());
            statement.setBoolean(8, restricted);
            statement.setInt(9, 0);
            statement.setTimestamp(10, null);
            statement.setTimestamp(11, null);
            statement.setInt(12, 0);
            statement.setTimestamp(13, Timestamp.valueOf(now));
            statement.setTimestamp(14, Timestamp.valueOf(now));
            statement.executeUpdate();
        }
    }

    private static Path databaseRoot() {
        Path root = Path.of("vcampus-database");
        return Files.exists(root) ? root : Path.of("..", "vcampus-database");
    }
}
