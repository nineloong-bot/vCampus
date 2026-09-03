package edu.seu.vcampus.server.user.handler;

import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.MessageType;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.common.user.ChangePasswordCommand;
import edu.seu.vcampus.common.user.ChangeUserStatusCommand;
import edu.seu.vcampus.common.user.LoginCommand;
import edu.seu.vcampus.common.user.LoginResult;
import edu.seu.vcampus.common.user.ResetStudentPasswordCommand;
import edu.seu.vcampus.common.user.ResetTeacherPasswordCommand;
import edu.seu.vcampus.common.user.TeacherAccountApplicationCommand;
import edu.seu.vcampus.common.user.UpdateUserRoleCommand;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.common.user.UserSearchQuery;
import edu.seu.vcampus.common.user.UserSummary;
import edu.seu.vcampus.common.user.UserView;
import edu.seu.vcampus.server.persistence.ConnectionProvider;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.routing.ClientContext;
import edu.seu.vcampus.server.routing.MessageRouter;
import edu.seu.vcampus.server.routing.RequestDeduplicator;
import edu.seu.vcampus.server.security.AuthorizationPort;
import edu.seu.vcampus.server.security.ForbiddenException;
import edu.seu.vcampus.server.security.SessionExpiredException;
import edu.seu.vcampus.server.security.UserIdentity;
import edu.seu.vcampus.server.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class UserHandlerAuthorizationDeduplicationTest {
    private static final ClientContext CONTEXT =
            new ClientContext("connection-7", "127.0.0.1");
    private final CountingUsers users = new CountingUsers();
    private TransactionManager transactions;
    private MessageRouter allowed;

    @BeforeEach
    void createPersistentDeduplicator() throws Exception {
        Path data = Path.of("target", "test-data");
        Files.createDirectories(data);
        String url = "jdbc:ucanaccess://" + data.resolve(UUID.randomUUID() + ".accdb")
                + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true";
        ConnectionProvider provider = () -> DriverManager.getConnection(url);
        transactions = new TransactionManager(provider);
        try (var connection = provider.open()) {
            execute(connection, projectFile("schema", "001_common.sql"));
        }
        allowed = router(new AllowAllAuthorization());
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER_RESET_STUDENT_PASSWORD",
            "USER_RESET_TEACHER_PASSWORD", "USER_CHANGE_STATUS"})
    void completedAdminRequestCannotReplayWithoutCurrentPermission(String command) {
        Serializable body = bodyFor(command);
        String requestId = UUID.randomUUID().toString();
        assertThat(allowed.route(request(requestId, command, "token", body), CONTEXT)
                .success()).isTrue();

        DenyingAuthorization denying = new DenyingAuthorization();
        ResponseBody<?> rejected = router(denying).route(request(requestId, command,
                "unprivileged-token", body), CONTEXT);

        assertThat(rejected).extracting(value -> value.success(), value -> value.code())
                .containsExactly(false, "AUTH_FORBIDDEN");
        assertThat(denying.permissionCalls).isEqualTo(1);
        assertThat(users.count(command)).isEqualTo(1);
    }

    @Test
    void completedPasswordChangeCannotReplayWithoutCurrentSession() {
        String requestId = UUID.randomUUID().toString();
        ChangePasswordCommand body = new ChangePasswordCommand(
                "OldPass123".toCharArray(), "NewPass123".toCharArray());
        assertThat(allowed.route(request(requestId, "USER_CHANGE_PASSWORD", "token", body),
                CONTEXT).success()).isTrue();

        ExpiredAuthorization expired = new ExpiredAuthorization();
        ResponseBody<?> rejected = router(expired).route(request(requestId,
                "USER_CHANGE_PASSWORD", "expired-token", body), CONTEXT);

        assertThat(rejected).extracting(value -> value.success(), value -> value.code())
                .containsExactly(false, "AUTH_SESSION_EXPIRED");
        assertThat(expired.sessionCalls).isEqualTo(1);
        assertThat(users.count("USER_CHANGE_PASSWORD")).isEqualTo(1);
    }

    @Test
    void authenticatedActorIsStoredOnProtectedClaim() {
        String requestId = UUID.randomUUID().toString();
        assertThat(allowed.route(request(requestId, "USER_RESET_STUDENT_PASSWORD", "token",
                bodyFor("USER_RESET_STUDENT_PASSWORD")), CONTEXT).success()).isTrue();

        String actor = transactions.inTransaction(connection -> {
            try (var statement = connection.prepareStatement(
                    "SELECT userId FROM tblRequestDedup WHERE requestId=?")) {
                statement.setString(1, requestId);
                try (var rows = statement.executeQuery()) {
                    rows.next();
                    return rows.getString(1);
                }
            }
        });
        assertThat(actor).isEqualTo("admin");
    }

    @Test
    void deduplicatorFailureRetainsAuthenticatedActorAndCommandTargetForAudit() {
        CountingUsers auditedUsers = new CountingUsers();
        TransactionManager failing = new TransactionManager(() -> {
            throw new SQLException("dedup database unavailable");
        });
        MessageRouter router = new MessageRouter(Map.of());
        new UserHandlers(router, auditedUsers, new AllowAllAuthorization(),
                new RequestDeduplicator(failing));

        ResponseBody<?> response = router.route(request(UUID.randomUUID().toString(),
                "USER_RESET_STUDENT_PASSWORD", "token",
                bodyFor("USER_RESET_STUDENT_PASSWORD")), CONTEXT);

        assertThat(response.code()).isEqualTo("COMMON_INTERNAL_ERROR");
        assertThat(auditedUsers.rejectedActor).isEqualTo("admin");
        assertThat(auditedUsers.rejectedTarget).isEqualTo("target");
    }

    private MessageRouter router(AuthorizationPort authorization) {
        MessageRouter router = new MessageRouter(Map.of());
        new UserHandlers(router, users, authorization,
                new RequestDeduplicator(transactions));
        return router;
    }

    private static Message request(String id, String command, String token, Serializable body) {
        return new Message(id, MessageType.REQUEST, command, token, body, 0);
    }

    private static Serializable bodyFor(String command) {
        return switch (command) {
            case "USER_RESET_STUDENT_PASSWORD" ->
                    new ResetStudentPasswordCommand("target", 0);
            case "USER_RESET_TEACHER_PASSWORD" ->
                    new ResetTeacherPasswordCommand("target", 0);
            default -> new ChangeUserStatusCommand(
                    "target", AccountStatus.DISABLED, "reviewed", 0);
        };
    }

    private static Path projectFile(String folder, String name) {
        Path fromModule = Path.of("..", "vcampus-database", folder, name);
        return Files.exists(fromModule) ? fromModule : Path.of("vcampus-database", folder, name);
    }

    private static void execute(java.sql.Connection connection, Path path) throws Exception {
        for (String sql : Files.readString(path).split(";")) {
            if (!sql.isBlank()) try (var statement = connection.createStatement()) {
                statement.execute(sql.strip());
            }
        }
    }

    private static final class AllowAllAuthorization implements AuthorizationPort {
        private static final UserIdentity ADMIN = new UserIdentity(
                "admin", "ADMIN", UserRole.ADMIN, AccountStatus.ACTIVE);
        @Override public UserIdentity requireSession(String token) { return ADMIN; }
        @Override public void requirePermission(String token, String permission) { }
    }

    private static final class DenyingAuthorization implements AuthorizationPort {
        private int permissionCalls;
        @Override public UserIdentity requireSession(String token) { throw new AssertionError(); }
        @Override public void requirePermission(String token, String permission) {
            permissionCalls++;
            throw new ForbiddenException();
        }
    }

    private static final class ExpiredAuthorization implements AuthorizationPort {
        private int sessionCalls;
        @Override public UserIdentity requireSession(String token) {
            sessionCalls++;
            throw new SessionExpiredException();
        }
        @Override public void requirePermission(String token, String permission) {
            throw new AssertionError();
        }
    }

    private static final class CountingUsers implements UserService {
        private static final UserView VIEW = new UserView("target", "TARGET", UserRole.ADMIN,
                AccountStatus.ACTIVE, false, null, 1, LocalDateTime.MIN, LocalDateTime.MIN);
        private final ConcurrentHashMap<String, AtomicInteger> counts = new ConcurrentHashMap<>();
        private String rejectedActor;
        private String rejectedTarget;
        int count(String command) { return counts.getOrDefault(command, new AtomicInteger()).get(); }
        private void hit(String command) { counts.computeIfAbsent(command, key -> new AtomicInteger()).incrementAndGet(); }
        @Override public void changePassword(String token, ChangePasswordCommand command) { hit("USER_CHANGE_PASSWORD"); }
        @Override public UserView updateRole(UpdateUserRoleCommand command) { hit("USER_UPDATE_ROLE"); return VIEW; }
        @Override public UserView resetStudentPassword(String actorUserId,
                ResetStudentPasswordCommand command, ClientContext context) {
            hit("USER_RESET_STUDENT_PASSWORD");
            return VIEW;
        }
        @Override public UserView resetTeacherPassword(String actorUserId,
                ResetTeacherPasswordCommand command, ClientContext context) {
            hit("USER_RESET_TEACHER_PASSWORD");
            return VIEW;
        }
        @Override public UserView changeStatus(ChangeUserStatusCommand command) { hit("USER_CHANGE_STATUS"); return VIEW; }
        @Override public UserView applyForTeacherAccount(TeacherAccountApplicationCommand command) { return VIEW; }
        @Override public LoginResult login(LoginCommand command, ClientContext context) { return new LoginResult("token", VIEW, Set.of(), false); }
        @Override public void logout(String token) { }
        @Override public UserView getCurrentUser(String token) { return VIEW; }
        @Override public PageResult<UserSummary> searchUsers(UserSearchQuery query) { return new PageResult<>(java.util.List.of(), 0, 10, 0); }
        @Override public void revokeSessionsForUser(String userId) { }
        @Override public void auditRejectedRequest(String actor, String action, String target,
                RuntimeException failure, ClientContext context) {
            rejectedActor = actor;
            rejectedTarget = target;
        }
    }
}
