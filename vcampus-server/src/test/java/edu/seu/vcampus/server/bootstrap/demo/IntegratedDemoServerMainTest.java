package edu.seu.vcampus.server.bootstrap.demo;

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
import edu.seu.vcampus.common.user.ChangePasswordCommand;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.server.bootstrap.ApplicationRuntime;
import edu.seu.vcampus.server.routing.ClientContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntegratedDemoServerMainTest {
    private static final String DEMO_PASSWORD = "DemoPassword7";
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

        LoginResult administrator = login(first, "DEMO_ADMIN", DEMO_PASSWORD);
        assertThat(administrator.user().role()).isEqualTo(UserRole.ADMIN);
        assertThat(administrator.mustChangePassword()).isTrue();
        assertThat(administrator.permissions()).contains(
                "USER_READ_ALL", "USER_ROLE_WRITE", "USER_STATUS_WRITE", "USER_AUDIT_READ");

        LoginResult student = login(first, "DEMO_STUDENT", DEMO_PASSWORD);
        assertThat(student.user().userId()).isEqualTo("demo-student");
        assertThat(student.user().role()).isEqualTo(UserRole.STUDENT);
        assertThat(student.mustChangePassword()).isFalse();

        LoginResult teacher = login(first, "DEMO_TEACHER", DEMO_PASSWORD);
        assertThat(teacher.user().userId()).isEqualTo("demo-teacher");
        assertThat(teacher.user().role()).isEqualTo(UserRole.TEACHER);

        List<TermView> terms = data(route(first, "COURSE_TERM_LIST",
                student.sessionToken(), EmptyRequest.INSTANCE));
        assertThat(terms).hasSize(1);
        PageResult<OfferingSummary> offerings = data(route(first, "COURSE_SEARCH_OFFERINGS",
                student.sessionToken(), new OfferingSearchQuery(
                        terms.getFirst().termId(), "", null, false, 0, 20)));
        assertThat(offerings.items()).hasSizeGreaterThan(1)
                .allMatch(offering -> "OPEN".equals(offering.offeringStatus()));

        List<EnrollmentView> enrollments = data(route(first, "COURSE_GET_MY_ENROLLMENTS",
                student.sessionToken(), EmptyRequest.INSTANCE));
        assertThat(enrollments).anySatisfy(enrollment -> {
            assertThat(enrollment.studentId()).isEqualTo("demo-student");
            assertThat(enrollment.enrollmentStatus()).isEqualTo("ACTIVE");
        });
        Set<String> enrolledOfferingIds = enrollments.stream()
                .filter(enrollment -> "ACTIVE".equals(enrollment.enrollmentStatus()))
                .map(EnrollmentView::offeringId).collect(java.util.stream.Collectors.toSet());
        assertThat(offerings.items()).anyMatch(offering ->
                !enrolledOfferingIds.contains(offering.offeringId()));

        List<ScheduleItem> schedule = data(route(first, "COURSE_GET_MY_SCHEDULE",
                teacher.sessionToken(), EmptyRequest.INSTANCE));
        assertThat(schedule).hasSizeGreaterThan(1);

        DemoSnapshot before = snapshot(database);
        try (Connection connection = connection(database)) {
            assertThat(loginIds(connection))
                    .contains("DEMO_STUDENT", "DEMO_TEACHER", "DEMO_ADMIN");
            assertThat(roleOf(connection, "DEMO_STUDENT")).isEqualTo("STUDENT");
            assertThat(roleOf(connection, "DEMO_TEACHER")).isEqualTo("TEACHER");
            assertThat(roleOf(connection, "DEMO_ADMIN")).isEqualTo("ADMIN");
            assertThat(activeEnrollmentCount(connection, "demo-student")).isPositive();
            assertThat(openOfferingCount(connection)).isGreaterThan(1);
            assertThat(openUnselectedOfferingCount(connection, "demo-student")).isPositive();
            assertThat(openEnrollmentTermCount(connection, CLOCK.instant())).isEqualTo(1);
        }

        ApplicationRuntime second = IntegratedDemoServerMain.prepare(
                database, databaseRoot(), CLOCK);
        LoginResult secondStudent = login(second, "DEMO_STUDENT", DEMO_PASSWORD);
        List<TermView> repeatedTerms = data(route(second, "COURSE_TERM_LIST",
                secondStudent.sessionToken(), EmptyRequest.INSTANCE));
        assertThat(repeatedTerms).hasSize(1);
        assertThat(snapshot(database)).isEqualTo(before);
    }

    @Test
    void demoAdministratorMustChoosePolicyCompliantPasswordAndRelogin() throws Exception {
        Path database = temporaryDirectory.resolve("course-user-demo.accdb");
        ApplicationRuntime runtime = IntegratedDemoServerMain.prepare(
                database, databaseRoot(), CLOCK);
        LoginResult restricted = login(runtime, "DEMO_ADMIN", DEMO_PASSWORD);
        assertThat(restricted.mustChangePassword()).isTrue();

        ResponseBody<?> rejected = route(runtime, "USER_CHANGE_PASSWORD",
                restricted.sessionToken(), new ChangePasswordCommand(
                        DEMO_PASSWORD.toCharArray(), "short".toCharArray()));
        assertThat(rejected.success()).isFalse();
        assertThat(rejected.code()).isEqualTo("AUTH_PASSWORD_POLICY_VIOLATION");

        ResponseBody<?> changed = route(runtime, "USER_CHANGE_PASSWORD",
                restricted.sessionToken(), new ChangePasswordCommand(
                        DEMO_PASSWORD.toCharArray(), "DemoChanged8".toCharArray()));
        assertThat(changed.success()).isTrue();
        assertThat(route(runtime, "USER_LOGIN", null,
                new LoginCommand("DEMO_ADMIN", DEMO_PASSWORD.toCharArray(), "old-password"))
                .success()).isFalse();
        LoginResult relogged = login(runtime, "DEMO_ADMIN", "DemoChanged8");
        assertThat(relogged.mustChangePassword()).isFalse();
        assertThat(relogged.user().role()).isEqualTo(UserRole.ADMIN);
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

    private static DemoSnapshot snapshot(Path database) throws Exception {
        try (Connection connection = connection(database)) {
            return new DemoSnapshot(ids(connection, "tblUser", "userId", "loginId LIKE 'DEMO_%'"),
                    ids(connection, "tblTerm", "termId", "termCode='DEMO-TERM'"),
                    ids(connection, "tblCourse", "courseId", "courseCode LIKE 'DEMO-%'"),
                    ids(connection, "tblCourseOffering", "offeringId", "className LIKE 'Demo-%'"),
                    ids(connection, "tblEnrollment", "enrollmentId", "studentId='demo-student'"));
        }
    }

    private static List<String> ids(Connection connection, String table, String column,
                                    String where) throws Exception {
        List<String> values = new ArrayList<>();
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery(
                     "SELECT " + column + " FROM " + table + " WHERE " + where
                             + " ORDER BY " + column)) {
            while (rows.next()) values.add(rows.getString(1));
        }
        return List.copyOf(values);
    }

    private static List<String> loginIds(Connection connection) throws Exception {
        return ids(connection, "tblUser", "loginId", "loginId LIKE 'DEMO_%'");
    }

    private static String roleOf(Connection connection, String loginId) throws Exception {
        try (var statement = connection.prepareStatement(
                "SELECT roleCode FROM tblUser WHERE loginId=?")) {
            statement.setString(1, loginId);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getString(1);
            }
        }
    }

    private static int activeEnrollmentCount(Connection connection, String studentId)
            throws Exception {
        return count(connection,
                "SELECT COUNT(*) FROM tblEnrollment WHERE studentId=? AND enrollmentStatus='ACTIVE'",
                studentId);
    }

    private static int openOfferingCount(Connection connection) throws Exception {
        return count(connection,
                "SELECT COUNT(*) FROM tblCourseOffering WHERE offeringStatus='OPEN'", null);
    }

    private static int openUnselectedOfferingCount(Connection connection, String studentId)
            throws Exception {
        return count(connection, """
                SELECT COUNT(*) FROM tblCourseOffering o
                WHERE o.offeringStatus='OPEN'
                  AND NOT EXISTS (
                    SELECT 1 FROM tblEnrollment e
                    WHERE e.offeringId=o.offeringId AND e.studentId=?
                      AND e.enrollmentStatus='ACTIVE')
                """, studentId);
    }

    private static int openEnrollmentTermCount(Connection connection, Instant now)
            throws Exception {
        try (var statement = connection.prepareStatement("""
                SELECT COUNT(*) FROM tblTerm
                WHERE termStatus='ACTIVE' AND enrollmentStartAt<=? AND enrollmentEndAt>?
                """)) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setTimestamp(2, Timestamp.from(now));
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getInt(1);
            }
        }
    }

    private static int count(Connection connection, String sql, String argument) throws Exception {
        try (var statement = connection.prepareStatement(sql)) {
            if (argument != null) statement.setString(1, argument);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getInt(1);
            }
        }
    }

    private static Connection connection(Path database) throws Exception {
        return DriverManager.getConnection("jdbc:ucanaccess://" + database);
    }

    private record DemoSnapshot(List<String> userIds, List<String> termIds,
                                List<String> courseIds, List<String> offeringIds,
                                List<String> enrollmentIds) { }

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
