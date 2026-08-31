package edu.seu.vcampus.server.user.service;

import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.common.user.LoginCommand;
import edu.seu.vcampus.common.user.ResetStudentPasswordCommand;
import edu.seu.vcampus.common.user.UpdateUserRoleCommand;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.concurrency.ResourceKey;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
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
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudentPasswordResetTest {
    private static final String ADMIN_ID = "00000000-0000-0000-0000-000000000001";
    private static final ClientContext ADMIN_CONTEXT =
            new ClientContext("admin-connection", "10.0.0.10");
    private TransactionManager transactions;
    private UserRepository repository;
    private UserService service;
    private SessionRegistry sessions;

    @BeforeEach
    void createService() throws Exception {
        Path database = Path.of("target", "test-data", UUID.randomUUID() + ".accdb");
        Files.createDirectories(database.getParent());
        ConnectionProvider connections = () -> DriverManager.getConnection(
                "jdbc:ucanaccess://" + database
                        + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true");
        transactions = new TransactionManager(connections);
        try (var connection = connections.open()) {
            execute(connection, projectFile("schema", "010_user.sql"));
            execute(connection, projectFile("seed", "010_roles_permissions.sql"));
        }
        repository = new AccessUserRepository();
        sessions = new SessionRegistry();
        service = new UserServiceImpl(transactions, new StripedResourceLockManager(), repository,
                new AccessAuditRepository(), new PasswordHasher(), sessions,
                java.time.Clock.systemUTC());
    }

    @Test
    void administratorResetsOnlyStudentPasswordAndRevokesExistingSession() {
        UserAccount student = account("RESET_STUDENT", UserRole.STUDENT);
        insert(student);
        String oldToken = login(student.loginId(), "Pass1234").sessionToken();
        UserAccount afterLogin = find(student.userId());
        UserAccount locked = new UserAccount(afterLogin.userId(), afterLogin.loginId(),
                afterLogin.passwordHash(), afterLogin.passwordSalt(), afterLogin.passwordIterations(),
                afterLogin.role(), afterLogin.accountStatus(), false, 4,
                LocalDateTime.now().plusMinutes(5), afterLogin.lastLoginAt(),
                afterLogin.rowVersion(), afterLogin.createdAt(), afterLogin.updatedAt());
        transactions.inTransaction(connection -> {
            repository.updateWithVersion(connection, locked, afterLogin.rowVersion());
            return null;
        });

        var view = service.resetStudentPassword(ADMIN_ID,
                new ResetStudentPasswordCommand(student.userId(), afterLogin.rowVersion() + 1),
                ADMIN_CONTEXT);

        assertThat(view.mustChangePassword()).isTrue();
        assertThat(view.toString()).doesNotContain("12345678", "password", "salt", "token");
        assertThatThrownBy(() -> sessions.requireSession(oldToken))
                .isInstanceOf(SessionExpiredException.class);
        assertThatThrownBy(() -> login(student.loginId(), "Pass1234"))
                .isInstanceOf(InvalidCredentialsException.class);
        var resetLogin = login(student.loginId(), "12345678");
        assertThat(resetLogin.mustChangePassword()).isTrue();
        assertThat(resetLogin.permissions()).isEmpty();
        UserAccount stored = find(student.userId());
        assertThat(stored.failedLoginCount()).isZero();
        assertThat(stored.lockedUntil()).isNull();
        assertThat(stored.mustChangePassword()).isTrue();
        assertAudit("USER_PASSWORD_RESET", "SUCCESS", ADMIN_ID, student.userId());
    }

    @Test
    void queuedOldSessionPasswordChangeCannotOverwriteAdministratorReset() throws Exception {
        UserAccount student = account("RESET_RACE_STUDENT", UserRole.STUDENT);
        insert(student);
        service.resetStudentPassword(ADMIN_ID,
                new ResetStudentPasswordCommand(student.userId(), student.rowVersion()),
                ADMIN_CONTEXT);
        String oldToken = login(student.loginId(), "12345678").sessionToken();
        UserAccount beforeReset = find(student.userId());
        CoordinatingUserLockManager locks = new CoordinatingUserLockManager();
        UserService racing = new UserServiceImpl(transactions, locks, repository,
                new AccessAuditRepository(), new PasswordHasher(), sessions,
                java.time.Clock.systemUTC());

        try (var executor = Executors.newFixedThreadPool(2)) {
            var reset = executor.submit(() -> racing.resetStudentPassword(ADMIN_ID,
                    new ResetStudentPasswordCommand(student.userId(), beforeReset.rowVersion()),
                    ADMIN_CONTEXT));
            assertThat(locks.awaitFirstHolder()).isTrue();
            var change = executor.submit(() -> racing.changePassword(oldToken,
                    new edu.seu.vcampus.common.user.ChangePasswordCommand(
                            "12345678".toCharArray(), "StudentWins9".toCharArray())));
            assertThat(locks.awaitQueuedRequest()).isTrue();

            locks.releaseFirstHolder();
            assertThat(reset.get(10, TimeUnit.SECONDS).mustChangePassword()).isTrue();
            assertThatThrownBy(() -> change.get(10, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(SessionExpiredException.class);
        }

        assertThatThrownBy(() -> login(student.loginId(), "StudentWins9"))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThat(login(student.loginId(), "12345678").mustChangePassword()).isTrue();
    }

    @Test
    void rejectsNonStudentMissingAndStaleTargetsWithoutChangingAccounts() {
        UserAccount teacher = account("RESET_TEACHER", UserRole.TEACHER);
        insert(teacher);

        assertThatThrownBy(() -> service.resetStudentPassword(ADMIN_ID,
                new ResetStudentPasswordCommand(teacher.userId(), 0), ADMIN_CONTEXT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("COMMON_VALIDATION_FAILED");
        assertThatThrownBy(() -> service.resetStudentPassword(ADMIN_ID,
                new ResetStudentPasswordCommand("missing", 0), ADMIN_CONTEXT))
                .hasMessage("USER_NOT_FOUND");
        UserAccount student = account("STALE_STUDENT", UserRole.STUDENT);
        insert(student);
        assertThatThrownBy(() -> service.resetStudentPassword(ADMIN_ID,
                new ResetStudentPasswordCommand(student.userId(), 9), ADMIN_CONTEXT))
                .isInstanceOf(ConcurrentModificationException.class);
        assertThat(find(teacher.userId()).role()).isEqualTo(UserRole.TEACHER);
    }

    @Test
    void retiredRoleServiceCannotMutateRoleVersionStatusOrSession() {
        UserAccount teacher = account("ROLE_TEACHER", UserRole.TEACHER);
        insert(teacher);
        String token = login(teacher.loginId(), "Pass1234").sessionToken();
        UserAccount before = find(teacher.userId());

        assertThatThrownBy(() -> service.updateRole(ADMIN_ID,
                new UpdateUserRoleCommand(teacher.userId(), UserRole.ADMIN,
                        before.rowVersion()), ADMIN_CONTEXT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("COMMON_VALIDATION_FAILED");

        UserAccount after = find(teacher.userId());
        assertThat(after.role()).isEqualTo(before.role());
        assertThat(after.accountStatus()).isEqualTo(before.accountStatus());
        assertThat(after.rowVersion()).isEqualTo(before.rowVersion());
        assertThat(sessions.requireSession(token).userId()).isEqualTo(teacher.userId());
        assertAudit("USER_UPDATE_ROLE", "COMMON_VALIDATION_FAILED", ADMIN_ID,
                teacher.userId());
    }

    private edu.seu.vcampus.common.user.LoginResult login(String loginId, String password) {
        return service.login(new LoginCommand(loginId, password.toCharArray(), "client"),
                new ClientContext("connection", "127.0.0.1"));
    }

    private UserAccount find(String userId) {
        return transactions.inTransaction(connection -> repository.findById(connection, userId)
                .orElseThrow());
    }

    private void assertAudit(String action, String result, String actor, String target) {
        transactions.inTransaction(connection -> {
            try (var statement = connection.prepareStatement(
                    "SELECT userId,targetId,resultCode FROM tblAuditLog WHERE actionCode=?")) {
                statement.setString(1, action);
                try (var rows = statement.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isEqualTo(actor);
                    assertThat(rows.getString(2)).isEqualTo(target);
                    assertThat(rows.getString(3)).isEqualTo(result);
                    assertThat(rows.next()).isFalse();
                }
            }
            return null;
        });
    }

    private void insert(UserAccount account) {
        transactions.inTransaction(connection -> {
            repository.insert(connection, account);
            return null;
        });
    }

    private static UserAccount account(String loginId, UserRole role) {
        PasswordHash password = new PasswordHasher().hash("Pass1234".toCharArray());
        LocalDateTime now = LocalDateTime.of(2026, 8, 31, 10, 0);
        return new UserAccount(UUID.randomUUID().toString(), loginId, password.hash(),
                password.salt(), password.iterations(), role, AccountStatus.ACTIVE,
                false, 0, null, null, 0, now, now);
    }

    private static Path projectFile(String folder, String name) {
        Path fromModule = Path.of("..", "vcampus-database", folder, name);
        return Files.exists(fromModule) ? fromModule : Path.of("vcampus-database", folder, name);
    }

    private static void execute(java.sql.Connection connection, Path script) throws Exception {
        for (String sql : Files.readString(script).split(";")) {
            if (!sql.isBlank()) try (var statement = connection.createStatement()) {
                statement.execute(sql.strip());
            }
        }
    }

    private static final class CoordinatingUserLockManager implements ResourceLockManager {
        private final ReentrantLock lock = new ReentrantLock(true);
        private final CountDownLatch firstHolder = new CountDownLatch(1);
        private final CountDownLatch queuedRequest = new CountDownLatch(1);
        private final CountDownLatch releaseFirst = new CountDownLatch(1);
        private int calls;

        @Override public <T> T withLocks(List<ResourceKey> keys, Supplier<T> action) {
            int call;
            synchronized (this) { call = ++calls; }
            if (call == 2) queuedRequest.countDown();
            lock.lock();
            try {
                if (call == 1) {
                    firstHolder.countDown();
                    await(releaseFirst);
                }
                return action.get();
            } finally {
                lock.unlock();
            }
        }

        boolean awaitFirstHolder() throws InterruptedException {
            return firstHolder.await(10, TimeUnit.SECONDS);
        }
        boolean awaitQueuedRequest() throws InterruptedException {
            return queuedRequest.await(10, TimeUnit.SECONDS);
        }
        void releaseFirstHolder() { releaseFirst.countDown(); }
        private static void await(CountDownLatch latch) {
            try {
                if (!latch.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out coordinating user lock");
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(error);
            }
        }
    }
}
