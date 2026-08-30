package edu.seu.vcampus.server.user.handler;

import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.protocol.EmptyRequest;
import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.MessageType;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.common.user.ChangePasswordCommand;
import edu.seu.vcampus.common.user.ChangeUserStatusCommand;
import edu.seu.vcampus.common.user.LoginCommand;
import edu.seu.vcampus.common.user.LoginResult;
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
import edu.seu.vcampus.server.security.UserIdentity;
import edu.seu.vcampus.server.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class UserHandlerDeduplicationTest {
    private static final ClientContext CONTEXT = new ClientContext("connection-7", "127.0.0.1");
    private CountingUsers users;
    private MessageRouter router;
    private TransactionManager transactions;
    private ConnectionProvider connections;

    @BeforeEach
    void createRouterWithPersistentDeduplicator() throws Exception {
        Path data = Path.of("target", "test-data");
        Files.createDirectories(data);
        String url = "jdbc:ucanaccess://" + data.resolve(UUID.randomUUID() + ".accdb")
                + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true";
        connections = () -> DriverManager.getConnection(url);
        transactions = new TransactionManager(connections);
        try (var connection = connections.open()) {
            execute(connection, projectFile("schema", "001_common.sql"));
        }
        users = new CountingUsers();
        router = new MessageRouter(Map.of());
        new UserHandlers(router, users, new AllowAllAuthorization(),
                new RequestDeduplicator(transactions));
    }

    @Test
    void deduplicatesFiveWritesWhileTheirAuthenticationRemainsValid() {
        assertReplay("USER_REGISTER",
                new TeacherAccountApplicationCommand("TEACHER", "Password1".toCharArray()));
        assertReplay("USER_LOGOUT", EmptyRequest.INSTANCE);
        assertReplay("USER_CHANGE_PASSWORD", new ChangePasswordCommand(
                "OldPass123".toCharArray(), "NewPass123".toCharArray()));
        assertReplay("USER_UPDATE_ROLE",
                new UpdateUserRoleCommand("target", UserRole.TEACHER, 0));
        assertReplay("USER_CHANGE_STATUS", new ChangeUserStatusCommand(
                "target", AccountStatus.DISABLED, "reviewed", 0));
    }

    @Test
    void concurrentDuplicateRequestHasOneExecutionAndEquivalentResponses() throws Exception {
        Message request = request(UUID.randomUUID().toString(), "USER_CHANGE_STATUS",
                new ChangeUserStatusCommand("target", AccountStatus.DISABLED, "reviewed", 0));
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> router.route(request, CONTEXT));
            var second = executor.submit(() -> router.route(request, CONTEXT));

            ResponseBody<?> firstResponse = first.get(10, TimeUnit.SECONDS);
            ResponseBody<?> secondResponse = second.get(10, TimeUnit.SECONDS);
            assertThat(firstResponse).isEqualTo(secondResponse);
        }
        assertThat(users.count("USER_CHANGE_STATUS")).isEqualTo(1);
        assertThat(users.auditCount("USER_CHANGE_STATUS")).isEqualTo(1);
    }

    @Test
    void loginNeverPersistsItsTokenBearingResponse() {
        UserLoginHandler login = new UserLoginHandler(users);
        Message request = request(UUID.randomUUID().toString(), "USER_LOGIN",
                new LoginCommand("ADMIN", "Password1".toCharArray(), "client"));

        assertThat(login.handle(request, CONTEXT).success()).isTrue();
        long rows = transactions.inTransaction(connection -> {
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT COUNT(*) FROM tblRequestDedup")) {
                result.next();
                return result.getLong(1);
            }
        });
        assertThat(rows).isZero();
    }

    @Test
    void replaysStableFailureWithoutRepeatingBusinessOrFailureAuditPath() {
        users.failStatusChange = true;
        Message request = request(UUID.randomUUID().toString(), "USER_CHANGE_STATUS",
                new ChangeUserStatusCommand("target", AccountStatus.DISABLED, "reviewed", 0));

        ResponseBody<?> first = router.route(request, CONTEXT);
        ResponseBody<?> replay = router.route(request, CONTEXT);

        assertThat(first.code()).isEqualTo("USER_STATUS_CONFLICT");
        assertThat(replay).isEqualTo(first);
        assertThat(users.count("USER_CHANGE_STATUS")).isEqualTo(1);
        assertThat(users.auditCount("USER_CHANGE_STATUS")).isEqualTo(1);
    }

    @Test
    void logoutClaimFailureIsSafelyReturnedAndAuditedWithoutCallingBusinessService() {
        MessageRouter failingRouter = logoutRouterWhoseClaimFails(false);

        ResponseBody<?> response = failingRouter.route(request(UUID.randomUUID().toString(),
                "USER_LOGOUT", EmptyRequest.INSTANCE), CONTEXT);

        assertThat(response.code()).isEqualTo("COMMON_INTERNAL_ERROR");
        assertThat(users.count("USER_LOGOUT")).isZero();
        assertThat(users.rejectedAuditCount).isEqualTo(1);
        assertThat(users.rejectedAction).isEqualTo("USER_LOGOUT");
        assertThat(users.rejectedActor).isNull();
        assertThat(users.rejectedTarget).isNull();
    }

    @Test
    void logoutAuditFailureDoesNotReplaceSafeDeduplicatorFailureResponse() {
        MessageRouter failingRouter = logoutRouterWhoseClaimFails(true);

        ResponseBody<?> response = failingRouter.route(request(UUID.randomUUID().toString(),
                "USER_LOGOUT", EmptyRequest.INSTANCE), CONTEXT);

        assertThat(response.code()).isEqualTo("COMMON_INTERNAL_ERROR");
        assertThat(users.count("USER_LOGOUT")).isZero();
        assertThat(users.rejectedAuditCount).isEqualTo(1);
    }

    @Test
    void duplicateLogoutRemainsSuccessfulAndCallsBusinessServiceOnce() {
        Message request = request(UUID.randomUUID().toString(),
                "USER_LOGOUT", EmptyRequest.INSTANCE);

        assertThat(router.route(request, CONTEXT).success()).isTrue();
        assertThat(router.route(request, CONTEXT).success()).isTrue();
        assertThat(users.count("USER_LOGOUT")).isEqualTo(1);
    }

    private MessageRouter logoutRouterWhoseClaimFails(boolean auditFails) {
        AtomicInteger opened = new AtomicInteger();
        TransactionManager failing = new TransactionManager(() -> {
            if (opened.incrementAndGet() == 2) {
                throw new SQLException("claim storage unavailable");
            }
            return connections.open();
        });
        users.failRejectedAudit = auditFails;
        MessageRouter failingRouter = new MessageRouter(Map.of());
        new UserHandlers(failingRouter, users, new AllowAllAuthorization(),
                new RequestDeduplicator(failing));
        return failingRouter;
    }

    private void assertReplay(String command, Serializable body) {
        Message request = request(UUID.randomUUID().toString(), command, body);
        ResponseBody<?> first = router.route(request, CONTEXT);
        ResponseBody<?> replay = router.route(request, CONTEXT);
        assertThat(replay).isEqualTo(first);
        assertThat(users.count(command)).isEqualTo(1);
        assertThat(users.auditCount(command)).isEqualTo(1);
    }

    private static Message request(String requestId, String command, Serializable body) {
        return new Message(requestId, MessageType.REQUEST, command, "token", body, 0);
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
        private static final UserIdentity IDENTITY = new UserIdentity(
                "admin", "ADMIN", UserRole.ADMIN, AccountStatus.ACTIVE);
        @Override public UserIdentity requireSession(String sessionToken) { return IDENTITY; }
        @Override public void requirePermission(String sessionToken, String permissionCode) { }
    }

    private static final class CountingUsers implements UserService {
        private static final UserView VIEW = new UserView("target", "TARGET", UserRole.ADMIN,
                AccountStatus.ACTIVE, false, null, 1, LocalDateTime.MIN, LocalDateTime.MIN);
        private final ConcurrentHashMap<String, AtomicInteger> counts = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, AtomicInteger> auditCounts = new ConcurrentHashMap<>();
        private boolean failStatusChange;
        private boolean failRejectedAudit;
        private int rejectedAuditCount;
        private String rejectedAction;
        private String rejectedActor;
        private String rejectedTarget;
        int count(String command) { return counts.getOrDefault(command, new AtomicInteger()).get(); }
        int auditCount(String command) { return auditCounts.getOrDefault(command, new AtomicInteger()).get(); }
        private void hit(String command) {
            counts.computeIfAbsent(command, ignored -> new AtomicInteger()).incrementAndGet();
            auditCounts.computeIfAbsent(command, ignored -> new AtomicInteger()).incrementAndGet();
        }
        @Override public UserView applyForTeacherAccount(TeacherAccountApplicationCommand command) { hit("USER_REGISTER"); return VIEW; }
        @Override public LoginResult login(LoginCommand command, ClientContext context) { return new LoginResult("secret-token", VIEW, Set.of(), false); }
        @Override public void logout(String sessionToken) { hit("USER_LOGOUT"); }
        @Override public UserView getCurrentUser(String sessionToken) { return VIEW; }
        @Override public void changePassword(String sessionToken, ChangePasswordCommand command) { hit("USER_CHANGE_PASSWORD"); }
        @Override public PageResult<UserSummary> searchUsers(UserSearchQuery query) { return new PageResult<>(java.util.List.of(), 0, 10, 0); }
        @Override public UserView updateRole(UpdateUserRoleCommand command) { hit("USER_UPDATE_ROLE"); return VIEW; }
        @Override public UserView changeStatus(ChangeUserStatusCommand command) {
            hit("USER_CHANGE_STATUS");
            if (failStatusChange) throw new IllegalStateException("USER_STATUS_CONFLICT");
            return VIEW;
        }
        @Override public void revokeSessionsForUser(String userId) { }
        @Override public void auditRejectedRequest(String actor, String action, String target,
                RuntimeException failure, ClientContext context) {
            rejectedAuditCount++;
            rejectedAction = action;
            rejectedActor = actor;
            rejectedTarget = target;
            if (failRejectedAudit) throw new IllegalStateException("audit unavailable");
        }
    }
}
