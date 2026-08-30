package edu.seu.vcampus.server.user.service;

import edu.seu.vcampus.common.user.ChangePasswordCommand;
import edu.seu.vcampus.common.user.LoginCommand;
import edu.seu.vcampus.common.user.LoginResult;
import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.MessageType;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.server.concurrency.ResourceKey;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.persistence.ConnectionProvider;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.routing.ClientContext;
import edu.seu.vcampus.server.routing.MessageRouter;
import edu.seu.vcampus.server.routing.RequestDeduplicator;
import edu.seu.vcampus.server.security.AuthorizationService;
import edu.seu.vcampus.server.security.InvalidCredentialsException;
import edu.seu.vcampus.server.security.SessionExpiredException;
import edu.seu.vcampus.server.session.SessionRegistry;
import edu.seu.vcampus.server.user.domain.UserAccount;
import edu.seu.vcampus.server.user.repository.AccessAuditRepository;
import edu.seu.vcampus.server.user.repository.AccessUserRepository;
import edu.seu.vcampus.server.user.repository.UserRepository;
import edu.seu.vcampus.server.user.handler.UserHandlers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static edu.seu.vcampus.common.user.AccountStatus.ACTIVE;
import static edu.seu.vcampus.common.user.UserRole.STUDENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordChangeTest {
    private UserService service;
    private SessionRegistry sessions;
    private TransactionManager transactions;
    private UserRepository users;
    private PasswordHasher hasher;

    @BeforeEach
    void setUp() throws Exception {
        Path data = Path.of("target", "test-data");
        Files.createDirectories(data);
        String url = "jdbc:ucanaccess://" + data.resolve(UUID.randomUUID() + ".accdb")
                + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true";
        ConnectionProvider connections = () -> DriverManager.getConnection(url);
        transactions = new TransactionManager(connections);
        try (var connection = connections.open()) {
            for (Path script : new Path[] {projectFile("schema", "001_common.sql"),
                    projectFile("schema", "010_user.sql"),
                    projectFile("seed", "010_roles_permissions.sql")}) {
                executeScript(connection, script);
            }
        }
        users = new AccessUserRepository();
        hasher = new PasswordHasher();
        PasswordHash hash = hasher.hash("12345678".toCharArray());
        LocalDateTime now = LocalDateTime.now();
        transactions.inTransaction(connection -> {
            users.insert(connection, new UserAccount("student-id", "213242478", hash.hash(),
                    hash.salt(), hash.iterations(), STUDENT, ACTIVE, true, 0, null, null,
                    0, now, now));
            return null;
        });
        sessions = new SessionRegistry();
        service = new UserServiceImpl(transactions, new StripedResourceLockManager(), users,
                new AccessAuditRepository(), hasher, sessions, java.time.Clock.systemUTC());
    }

    @Test
    void changingPasswordRevokesRestrictedSessionAndInvalidatesOldPassword() {
        String token = login("12345678").sessionToken();
        String secondToken = login("12345678").sessionToken();

        service.changePassword(token,
                new ChangePasswordCommand("12345678".toCharArray(), "NewPass123".toCharArray()));

        assertThatThrownBy(() -> sessions.requireSession(token))
                .isInstanceOf(SessionExpiredException.class);
        assertThatThrownBy(() -> sessions.requireSession(secondToken))
                .isInstanceOf(SessionExpiredException.class);
        assertThatThrownBy(() -> login("12345678"))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThat(login("NewPass123").mustChangePassword()).isFalse();
    }

    @Test
    void passwordChangeRevokesSessionIssuedFromConcurrentOldPasswordLogin() throws Exception {
        String changeToken = login("12345678").sessionToken();
        CoordinatingLockManager locks = new CoordinatingLockManager();
        UserService racingService = new UserServiceImpl(transactions, locks, users,
                new AccessAuditRepository(), hasher, sessions, java.time.Clock.systemUTC());
        try (var executor = Executors.newFixedThreadPool(2)) {
            var login = executor.submit(() -> racingService.login(new LoginCommand("213242478",
                    "12345678".toCharArray(), "racing-client"),
                    new ClientContext("racing-connection", "127.0.0.1")));
            assertThat(locks.awaitLoginAuthentication()).isTrue();
            var change = executor.submit(() -> racingService.changePassword(changeToken,
                    new ChangePasswordCommand("12345678".toCharArray(), "NewPass123".toCharArray())));
            change.get(10, TimeUnit.SECONDS);
            locks.releaseLogin();
            LoginResult result = login.get(10, TimeUnit.SECONDS);

            assertThatThrownBy(() -> sessions.requireSession(result.sessionToken()))
                    .isInstanceOf(SessionExpiredException.class);
        }
    }

    @Test
    void failedPasswordChangeWritesSafeActorTargetAudit() {
        String token = login("12345678").sessionToken();

        assertThatThrownBy(() -> service.changePassword(token,
                new ChangePasswordCommand("WrongPass1".toCharArray(),
                        "NewPass123".toCharArray()),
                new ClientContext("connection", "10.0.0.20")))
                .isInstanceOf(InvalidCredentialsException.class);

        transactions.inTransaction(connection -> {
            try (var statement = connection.prepareStatement(
                    "SELECT userId, targetId, resultCode, clientAddress FROM tblAuditLog "
                            + "WHERE actionCode='USER_CHANGE_PASSWORD'")) {
                try (var result = statement.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString(1)).isEqualTo("student-id");
                    assertThat(result.getString(2)).isEqualTo("student-id");
                    assertThat(result.getString(3)).isEqualTo("AUTH_INVALID_CREDENTIALS");
                    assertThat(result.getString(4)).isEqualTo("10.0.0.20");
                }
            }
            return null;
        });
    }

    @Test
    void retryingPasswordChangeWithRevokedTokenRequiresAuthenticationAgain() {
        String token = login("12345678").sessionToken();
        MessageRouter router = new MessageRouter(Map.of());
        new UserHandlers(router, service, new AuthorizationService(sessions),
                new RequestDeduplicator(transactions));
        String requestId = UUID.randomUUID().toString();
        Message request = new Message(requestId, MessageType.REQUEST,
                "USER_CHANGE_PASSWORD", token, new ChangePasswordCommand(
                "12345678".toCharArray(), "NewPass123".toCharArray()), 0);
        ClientContext context = new ClientContext("connection", "127.0.0.1");

        ResponseBody<?> first = router.route(request, context);
        long versionAfterFirst = findAccount().rowVersion();
        long successAuditsAfterFirst = successPasswordChangeAudits();
        ResponseBody<?> retry = router.route(request, context);

        assertThat(first.success()).isTrue();
        assertThatThrownBy(() -> sessions.requireSession(token))
                .isInstanceOf(SessionExpiredException.class);
        assertThat(retry.code()).isEqualTo("AUTH_SESSION_EXPIRED");
        assertThat(findAccount().rowVersion()).isEqualTo(versionAfterFirst);
        assertThat(successPasswordChangeAudits()).isEqualTo(successAuditsAfterFirst)
                .isEqualTo(1);
        assertThat(login("NewPass123").sessionToken()).isNotBlank();
    }

    private UserAccount findAccount() {
        return transactions.inTransaction(connection ->
                users.findByNormalizedLoginId(connection, "213242478").orElseThrow());
    }

    private long successPasswordChangeAudits() {
        return transactions.inTransaction(connection -> {
            try (var statement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM tblAuditLog WHERE actionCode=? AND resultCode=?")) {
                statement.setString(1, "USER_CHANGE_PASSWORD");
                statement.setString(2, "SUCCESS");
                try (var rows = statement.executeQuery()) {
                    rows.next();
                    return rows.getLong(1);
                }
            }
        });
    }

    private edu.seu.vcampus.common.user.LoginResult login(String password) {
        return service.login(new LoginCommand("213242478", password.toCharArray(), "client"),
                new ClientContext("connection", "127.0.0.1"));
    }

    private static Path projectFile(String folder, String name) {
        Path fromModule = Path.of("..", "vcampus-database", folder, name);
        return Files.exists(fromModule) ? fromModule : Path.of("vcampus-database", folder, name);
    }

    private static void executeScript(java.sql.Connection connection, Path path) throws Exception {
        for (String sql : Files.readString(path).split(";")) {
            if (!sql.isBlank()) {
                try (var statement = connection.createStatement()) { statement.execute(sql.strip()); }
            }
        }
    }

    private static final class CoordinatingLockManager implements ResourceLockManager {
        private final AtomicBoolean pauseFirstLock = new AtomicBoolean(true);
        private final CountDownLatch authenticated = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        @Override public <T> T withLocks(List<ResourceKey> keys, Supplier<T> action) {
            T result = action.get();
            if (pauseFirstLock.compareAndSet(true, false)) {
                authenticated.countDown();
                await(release);
            }
            return result;
        }

        boolean awaitLoginAuthentication() throws InterruptedException {
            return authenticated.await(10, TimeUnit.SECONDS);
        }

        void releaseLogin() { release.countDown(); }

        private static void await(CountDownLatch latch) {
            try {
                if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("login did not resume");
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(error);
            }
        }
    }
}
