package edu.seu.vcampus.server.bootstrap;

import edu.seu.vcampus.common.course.CreateTermCommand;
import edu.seu.vcampus.common.protocol.EmptyRequest;
import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.MessageType;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.common.user.LoginCommand;
import edu.seu.vcampus.common.user.LoginResult;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.server.persistence.ConnectionProvider;
import edu.seu.vcampus.server.routing.ClientContext;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationRuntimeTest {
    private static final ClientContext CONTEXT = new ClientContext("connection", "127.0.0.1");

    @Test
    void composesUserAndCourseCommandsWithOneApplicationLockManager() throws Exception {
        Path database = Files.createTempDirectory("vcampus-runtime-").resolve("runtime.accdb");
        ConnectionProvider connections = () -> DriverManager.getConnection(
                "jdbc:ucanaccess://" + database + ";newDatabaseVersion=V2010");
        ApplicationRuntime runtime = ApplicationRuntime.create(connections, databaseRoot(), Clock.systemUTC());
        insertUser(connections, "STUDENT1", UserRole.STUDENT, false, "Password7");
        insertUser(connections, "ADMIN1", UserRole.ADMIN, false, "Password7");
        insertUser(connections, "RESTRICTED1", UserRole.STUDENT, true, "Password7");

        LoginResult student = login(runtime, "STUDENT1");
        assertThat(route(runtime, "COURSE_TERM_LIST", student.sessionToken(), EmptyRequest.INSTANCE).success())
                .isTrue();
        assertThat(route(runtime, "COURSE_TERM_CREATE", student.sessionToken(), term()).code())
                .isEqualTo("COMMON_FORBIDDEN");

        LoginResult administrator = login(runtime, "ADMIN1");
        ResponseBody<?> created = route(runtime, "COURSE_TERM_CREATE", administrator.sessionToken(), term());
        assertThat(created.success()).withFailMessage("course term creation code: %s", created.code())
                .isTrue();

        LoginResult restricted = login(runtime, "RESTRICTED1");
        assertThat(route(runtime, "COURSE_TERM_LIST", restricted.sessionToken(), EmptyRequest.INSTANCE).code())
                .isEqualTo("AUTH_INITIAL_PASSWORD_CHANGE_REQUIRED");
        assertThat(runtime.course().resourceLocks()).isSameAs(runtime.resourceLocks());
    }

    @Test
    void usesTheInjectedClockForCourseSessionExpiry() throws Exception {
        Path database = Files.createTempDirectory("vcampus-runtime-clock-").resolve("runtime.accdb");
        ConnectionProvider connections = () -> DriverManager.getConnection(
                "jdbc:ucanaccess://" + database + ";newDatabaseVersion=V2010");
        MutableClock clock = new MutableClock();
        ApplicationRuntime runtime = ApplicationRuntime.create(connections, databaseRoot(), clock);
        insertUser(connections, "STUDENT1", UserRole.STUDENT, false, "Password7");

        LoginResult student = login(runtime, "STUDENT1");
        assertThat(route(runtime, "COURSE_TERM_LIST", student.sessionToken(), EmptyRequest.INSTANCE).success())
                .isTrue();

        clock.advance(Duration.ofMinutes(31));

        assertThat(route(runtime, "COURSE_TERM_LIST", student.sessionToken(), EmptyRequest.INSTANCE).code())
                .isEqualTo("AUTH_SESSION_EXPIRED");
    }

    @Test
    void configuredIdleTimeoutControlsSessionExpiryInsteadOfTheThirtyMinuteDefault() throws Exception {
        Path database = Files.createTempDirectory("vcampus-runtime-timeout-").resolve("runtime.accdb");
        ConnectionProvider connections = () -> DriverManager.getConnection(
                "jdbc:ucanaccess://" + database + ";newDatabaseVersion=V2010");
        MutableClock clock = new MutableClock();
        ApplicationRuntime runtime = ApplicationRuntime.create(
                connections, databaseRoot(), clock, Duration.ofMinutes(7));
        insertUser(connections, "STUDENT1", UserRole.STUDENT, false, "Password7");

        LoginResult student = login(runtime, "STUDENT1");
        clock.advance(Duration.ofMinutes(6));
        assertThat(route(runtime, "COURSE_TERM_LIST", student.sessionToken(), EmptyRequest.INSTANCE).success())
                .isTrue();
        clock.advance(Duration.ofMinutes(8));
        assertThat(route(runtime, "COURSE_TERM_LIST", student.sessionToken(), EmptyRequest.INSTANCE).code())
                .isEqualTo("AUTH_SESSION_EXPIRED");
    }

    private static LoginResult login(ApplicationRuntime runtime, String loginId) {
        ResponseBody<?> response = route(runtime, "USER_LOGIN", null,
                new LoginCommand(loginId, "Password7".toCharArray(), "client-1"));
        assertThat(response.success()).isTrue();
        return (LoginResult) response.data();
    }

    private static ResponseBody<?> route(ApplicationRuntime runtime, String command, String token,
                                          Serializable body) {
        return runtime.router().route(new Message(UUID.randomUUID().toString(), MessageType.REQUEST,
                command, token, body, 0), CONTEXT);
    }

    private static CreateTermCommand term() {
        return new CreateTermCommand("2026-1", "Autumn", LocalDate.of(2026, 9, 1),
                LocalDate.of(2027, 1, 1), Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-31T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z"),
                Instant.parse("2026-09-30T00:00:00Z"), "PLANNED");
    }

    private static void insertUser(ConnectionProvider connections, String loginId, UserRole role,
                                   boolean restricted, String password) {
        LocalDateTime now = LocalDateTime.now();
        try (var connection = connections.open(); var statement = connection.prepareStatement("""
                INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, passwordIterations,
                    roleCode, accountStatus, mustChangePassword, failedLoginCount, lockedUntil,
                    lastLoginAt, rowVersion, createdAt, updatedAt)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            byte[] salt = "application-runtime".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            PBEKeySpec specification = new PBEKeySpec(password.toCharArray(), salt, 120_000, 256);
            byte[] derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(specification).getEncoded();
            specification.clearPassword();
            statement.setString(1, UUID.randomUUID().toString());
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
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private static Path databaseRoot() {
        Path root = Path.of("vcampus-database");
        return Files.exists(root) ? root : Path.of("..", "vcampus-database");
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-08-30T00:00:00Z");

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
