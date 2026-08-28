package edu.seu.vcampus.server.user.service;

import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.persistence.ConnectionProvider;
import edu.seu.vcampus.server.persistence.TransactionContext;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.routing.MessageHandler;
import edu.seu.vcampus.server.user.domain.UserAccount;
import edu.seu.vcampus.server.user.repository.AccessAuditRepository;
import edu.seu.vcampus.server.user.repository.AccessUserRepository;
import edu.seu.vcampus.server.user.repository.AuditRepository;
import edu.seu.vcampus.server.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Optional;
import java.util.UUID;

import static edu.seu.vcampus.common.user.AccountStatus.ACTIVE;
import static edu.seu.vcampus.common.user.UserRole.STUDENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserAccountProvisioningPortTest {
    private TransactionManager transactions;
    private UserRepository users;
    private UserAccountProvisioningPort port;

    @BeforeEach
    void createPortWithIsolatedAccessDatabase() throws Exception {
        Path testData = Path.of("target", "test-data");
        Files.createDirectories(testData);
        String url = "jdbc:ucanaccess://" + testData.resolve(UUID.randomUUID() + ".accdb")
                + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true";
        ConnectionProvider provider = () -> DriverManager.getConnection(url);
        transactions = new TransactionManager(provider);
        try (Connection connection = provider.open()) {
            executeScript(connection, projectFile("schema", "010_user.sql"));
            executeScript(connection, projectFile("seed", "010_roles_permissions.sql"));
        }
        users = new AccessUserRepository();
        AuditRepository audits = new AccessAuditRepository();
        port = new UserAccountProvisioningService(
                new StripedResourceLockManager(), users, audits, new PasswordHasher());
    }

    @Test
    void createsFixedStudentAccountUsingCallerOwnedTransaction() {
        char[] password = "12345678".toCharArray();

        ProvisionedUserAccount result = transactions.inTransaction(connection ->
                port.createStudentAccount(
                        new TransactionContext(rejectLifecycleOperations(connection)),
                        "213242478", password));

        assertThat(result.loginId()).isEqualTo("213242478");
        assertThat(result.role()).isEqualTo(STUDENT);
        assertThat(result.status()).isEqualTo(ACTIVE);
        assertThat(password).containsOnly('\0');
        UserAccount saved = findUser("213242478").orElseThrow();
        assertThat(saved.userId()).isEqualTo(result.userId());
        assertThat(saved.loginId()).isEqualTo("213242478");
        assertThat(saved.role()).isEqualTo(STUDENT);
        assertThat(saved.accountStatus()).isEqualTo(ACTIVE);
        assertThat(saved.mustChangePassword()).isTrue();
        assertThat(auditCount("STUDENT_ACCOUNT_PROVISIONED")).isEqualTo(1);
    }

    @Test
    void callerRollbackRemovesAccountAndAuditTogether() {
        char[] password = "12345678".toCharArray();

        assertThatThrownBy(() -> transactions.inTransaction(connection -> {
            port.createStudentAccount(new TransactionContext(connection),
                    "213242478", password);
            throw new InjectedAdmissionFailure();
        })).isInstanceOf(InjectedAdmissionFailure.class);

        assertThat(password).containsOnly('\0');
        assertThat(findUser("213242478")).isEmpty();
        assertThat(auditCount("STUDENT_ACCOUNT_PROVISIONED")).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"21324247", "243242478", "21324A478", "2132424780"})
    void rejectsInvalidCampusCardNumber(String campusCardNumber) {
        char[] password = "12345678".toCharArray();

        assertThatThrownBy(() -> transactions.inTransaction(connection ->
                port.createStudentAccount(new TransactionContext(connection),
                        campusCardNumber, password)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(password).containsOnly('\0');
        assertThat(findUser(campusCardNumber)).isEmpty();
        assertThat(auditCount("STUDENT_ACCOUNT_PROVISIONED")).isZero();
    }

    @Test
    void rejectsDuplicateLoginIdWithoutAddingAnotherAudit() {
        transactions.inTransaction(connection -> port.createStudentAccount(
                new TransactionContext(connection), "213242478",
                "12345678".toCharArray()));
        char[] duplicatePassword = "12345678".toCharArray();

        assertThatThrownBy(() -> transactions.inTransaction(connection ->
                port.createStudentAccount(new TransactionContext(connection),
                        "213242478", duplicatePassword)))
                .isInstanceOf(RuntimeException.class);

        assertThat(duplicatePassword).containsOnly('\0');
        assertThat(findUser("213242478")).isPresent();
        assertThat(userCount("213242478")).isEqualTo(1);
        assertThat(auditCount("STUDENT_ACCOUNT_PROVISIONED")).isEqualTo(1);
    }

    @Test
    void provisioningContractIsNotASocketHandler() {
        assertThat(MessageHandler.class.isAssignableFrom(UserAccountProvisioningPort.class))
                .isFalse();
        assertThat(MessageHandler.class.isAssignableFrom(UserAccountProvisioningService.class))
                .isFalse();
    }

    private Optional<UserAccount> findUser(String loginId) {
        return transactions.inTransaction(connection ->
                users.findByNormalizedLoginId(connection, loginId));
    }

    private long userCount(String loginId) {
        return count("SELECT COUNT(*) FROM tblUser WHERE loginId=?", loginId);
    }

    private long auditCount(String actionCode) {
        return count("SELECT COUNT(*) FROM tblAuditLog WHERE actionCode=?", actionCode);
    }

    private long count(String sql, String value) {
        return transactions.inTransaction(connection -> {
            try (var statement = connection.prepareStatement(sql)) {
                statement.setString(1, value);
                try (var result = statement.executeQuery()) {
                    result.next();
                    return result.getLong(1);
                }
            }
        });
    }

    private static Connection rejectLifecycleOperations(Connection delegate) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("commit")
                            || method.getName().equals("rollback")
                            || method.getName().equals("setAutoCommit")) {
                        throw new AssertionError("Port must not manage the transaction");
                    }
                    try {
                        return method.invoke(delegate, arguments);
                    } catch (InvocationTargetException error) {
                        throw error.getCause();
                    }
                });
    }

    private static Path projectFile(String folder, String name) {
        Path fromModule = Path.of("..", "vcampus-database", folder, name);
        return Files.exists(fromModule) ? fromModule
                : Path.of("vcampus-database", folder, name);
    }

    private static void executeScript(Connection connection, Path path) throws Exception {
        for (String sql : Files.readString(path).split(";")) {
            if (!sql.isBlank()) {
                try (var statement = connection.createStatement()) {
                    statement.execute(sql.strip());
                }
            }
        }
    }

    private static final class InjectedAdmissionFailure extends RuntimeException {
    }
}
