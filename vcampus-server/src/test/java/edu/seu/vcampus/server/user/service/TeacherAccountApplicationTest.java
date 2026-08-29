package edu.seu.vcampus.server.user.service;

import edu.seu.vcampus.common.user.TeacherAccountApplicationCommand;
import edu.seu.vcampus.common.user.UserView;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.persistence.ConnectionProvider;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.routing.ClientContext;
import edu.seu.vcampus.server.user.domain.UserAccount;
import edu.seu.vcampus.server.user.repository.AccessAuditRepository;
import edu.seu.vcampus.server.user.repository.AccessUserRepository;
import edu.seu.vcampus.server.user.repository.AuditRepository;
import edu.seu.vcampus.server.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static edu.seu.vcampus.common.user.AccountStatus.PENDING;
import static edu.seu.vcampus.common.user.UserRole.TEACHER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TeacherAccountApplicationTest {
    private TransactionManager transactions;
    private UserRepository users;
    private UserService service;

    @BeforeEach
    void createServiceWithIsolatedAccessDatabase() throws Exception {
        Path testData = Path.of("target", "test-data");
        Files.createDirectories(testData);
        String url = "jdbc:ucanaccess://" + testData.resolve(UUID.randomUUID() + ".accdb")
                + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true";
        ConnectionProvider provider = () -> DriverManager.getConnection(url);
        transactions = new TransactionManager(provider);
        try (var connection = provider.open()) {
            executeScript(connection, projectFile("schema", "010_user.sql"));
            executeScript(connection, projectFile("seed", "010_roles_permissions.sql"));
        }
        users = new AccessUserRepository();
        AuditRepository audits = new AccessAuditRepository();
        ResourceLockManager locks = new StripedResourceLockManager();
        service = new UserServiceImpl(
                transactions, locks, users, audits, new PasswordHasher());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "password",
            "12345678",
            "Short1",
            "A1234567890123456789012345678901234567890123456789012345678901234"
    })
    void rejectsPasswordThatViolatesPolicy(String candidate) {
        char[] password = candidate.toCharArray();
        var command = new TeacherAccountApplicationCommand("teacher01", password);

        assertThatThrownBy(() -> service.applyForTeacherAccount(command))
                .hasMessage("AUTH_PASSWORD_POLICY_VIOLATION");
        assertThat(password).containsOnly('\0');
        Optional<UserAccount> saved = transactions.inTransaction(connection ->
                users.findByNormalizedLoginId(connection, "TEACHER01"));
        assertThat(saved).isEmpty();
    }

    @Test
    @Timeout(30)
    void onlyOneConcurrentTeacherApplicationWins() throws Exception {
        List<char[]> submittedPasswords = Collections.synchronizedList(new ArrayList<>());
        List<Outcome<UserView>> results = concurrently(20, () -> {
            char[] password = "Password1".toCharArray();
            submittedPasswords.add(password);
            return service.applyForTeacherAccount(
                    new TeacherAccountApplicationCommand("teacher01", password));
        });

        assertThat(results.stream().filter(Outcome::isSuccess)).hasSize(1);
        assertThat(results.stream().filter(Outcome::isFailure)).hasSize(19)
                .allSatisfy(outcome -> assertThat(outcome.error())
                        .hasMessage("USER_LOGIN_ID_EXISTS"));
        assertThat(submittedPasswords).allSatisfy(
                password -> assertThat(password).containsOnly('\0'));
        UserView view = results.stream().filter(Outcome::isSuccess)
                .map(Outcome::value).findFirst().orElseThrow();
        assertThat(view.loginId()).isEqualTo("TEACHER01");
        assertThat(view.role()).isEqualTo(TEACHER);
        assertThat(view.accountStatus()).isEqualTo(PENDING);

        UserAccount saved = transactions.inTransaction(connection ->
                users.findByNormalizedLoginId(connection, "TEACHER01").orElseThrow());
        assertThat(saved.role()).isEqualTo(TEACHER);
        assertThat(saved.accountStatus()).isEqualTo(PENDING);
        assertThat(saved.mustChangePassword()).isFalse();
        assertThat(saved.passwordHash()).isNotEqualTo("Password1");
        assertThat(saved.passwordSalt()).isNotBlank();
        assertThat(saved.passwordIterations()).isEqualTo(120_000);
        long auditCount = transactions.inTransaction(connection -> {
            try (var statement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM tblAuditLog WHERE actionCode=? AND resultCode=?")) {
                statement.setString(1, "USER_REGISTER");
                statement.setString(2, "SUCCESS");
                try (var result = statement.executeQuery()) {
                    result.next();
                    return result.getLong(1);
                }
            }
        });
        assertThat(auditCount).isEqualTo(1);
        transactions.inTransaction(connection -> {
            try (var statement = connection.prepareStatement(
                    "SELECT userId, targetId FROM tblAuditLog "
                            + "WHERE actionCode='USER_REGISTER' AND resultCode='SUCCESS'")) {
                try (var result = statement.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString(1)).isNull();
                    assertThat(result.getString(2)).isEqualTo(view.userId());
                }
            }
            return null;
        });
    }

    @Test
    void failedApplicationIsAuditedAfterBusinessRollback() {
        var command = new TeacherAccountApplicationCommand(
                "bad login", "Password1".toCharArray());

        assertThatThrownBy(() -> service.applyForTeacherAccount(command,
                new ClientContext("connection", "10.0.0.8")))
                .hasMessage("COMMON_VALIDATION_FAILED");

        transactions.inTransaction(connection -> {
            try (var statement = connection.prepareStatement(
                    "SELECT userId, targetId, resultCode, clientAddress FROM tblAuditLog "
                            + "WHERE actionCode='USER_REGISTER'")) {
                try (var result = statement.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString(1)).isNull();
                    assertThat(result.getString(2)).isNull();
                    assertThat(result.getString(3)).isEqualTo("COMMON_VALIDATION_FAILED");
                    assertThat(result.getString(4)).isEqualTo("10.0.0.8");
                }
            }
            return null;
        });
    }

    private static <T> List<Outcome<T>> concurrently(
            int count, Callable<T> action) throws Exception {
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(count)) {
            List<Future<Outcome<T>>> futures = java.util.stream.IntStream.range(0, count)
                    .mapToObj(ignored -> executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        try {
                            return Outcome.<T>success(action.call());
                        } catch (Throwable error) {
                            return Outcome.<T>failure(error);
                        }
                    })).toList();
            ready.await();
            start.countDown();
            return futures.stream().map(TeacherAccountApplicationTest::await).toList();
        }
    }

    private static <T> T await(Future<T> future) {
        try {
            return future.get();
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private static Path projectFile(String folder, String name) {
        Path fromModule = Path.of("..", "vcampus-database", folder, name);
        return Files.exists(fromModule) ? fromModule
                : Path.of("vcampus-database", folder, name);
    }

    private static void executeScript(java.sql.Connection connection, Path path)
            throws Exception {
        for (String sql : Files.readString(path).split(";")) {
            if (!sql.isBlank()) {
                try (var statement = connection.createStatement()) {
                    statement.execute(sql.strip());
                }
            }
        }
    }

    private record Outcome<T>(T value, Throwable error) {
        static <T> Outcome<T> success(T value) {
            return new Outcome<>(value, null);
        }

        static <T> Outcome<T> failure(Throwable error) {
            return new Outcome<>(null, error);
        }

        boolean isSuccess() {
            return error == null;
        }

        boolean isFailure() {
            return error != null;
        }
    }
}
