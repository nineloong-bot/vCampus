package edu.seu.vcampus.server.user.service;

import edu.seu.vcampus.common.user.ChangePasswordCommand;
import edu.seu.vcampus.common.user.LoginCommand;
import edu.seu.vcampus.common.user.LoginResult;
import edu.seu.vcampus.server.concurrency.ResourceKey;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.persistence.ConnectionProvider;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.routing.ClientContext;
import edu.seu.vcampus.server.security.InvalidCredentialsException;
import edu.seu.vcampus.server.security.SessionExpiredException;
import edu.seu.vcampus.server.session.SessionRegistry;
import edu.seu.vcampus.server.user.domain.UserAccount;
import edu.seu.vcampus.server.user.repository.AccessAuditRepository;
import edu.seu.vcampus.server.user.repository.AccessUserRepository;
import edu.seu.vcampus.server.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;
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
            for (Path script : new Path[] {projectFile("schema", "010_user.sql"),
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
