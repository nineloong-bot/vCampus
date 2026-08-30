package edu.seu.vcampus.server.user.repository;

import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.common.user.UserSearchQuery;
import edu.seu.vcampus.server.persistence.ConnectionProvider;
import edu.seu.vcampus.server.persistence.PersistenceException;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.user.domain.UserAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.spec.KeySpec;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.ConcurrentModificationException;
import java.util.UUID;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import static edu.seu.vcampus.common.user.AccountStatus.ACTIVE;
import static edu.seu.vcampus.common.user.UserRole.ADMIN;
import static edu.seu.vcampus.common.user.UserRole.TEACHER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessUserRepositoryTest {
    private TransactionManager transactions;
    private UserRepository repository;

    @BeforeEach
    void createAccessDatabase() throws Exception {
        Path testData = Path.of("target", "test-data");
        Files.createDirectories(testData);
        Path database = testData.resolve(UUID.randomUUID() + ".accdb");
        String url = "jdbc:ucanaccess://" + database
                + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true";
        ConnectionProvider provider = () -> DriverManager.getConnection(url);
        transactions = new TransactionManager(provider);
        try (var connection = provider.open()) {
            executeScript(connection, schemaPath());
            executeScript(connection, seedPath());
        }
        repository = new AccessUserRepository();
    }

    @Test
    void rejectsDuplicateNormalizedLoginIdAndStaleVersion() {
        transactions.inTransaction(connection -> {
            repository.insert(connection, account("Alice", TEACHER));
            return null;
        });

        assertThatThrownBy(() -> transactions.inTransaction(connection -> {
            repository.insert(connection, account("alice", TEACHER));
            return null;
        })).isInstanceOf(DuplicateLoginIdException.class);

        UserAccount saved = transactions.inTransaction(connection ->
                repository.findByNormalizedLoginId(connection, "ALICE").orElseThrow());
        assertThat(saved.loginId()).isEqualTo("ALICE");

        transactions.inTransaction(connection -> {
            repository.updateWithVersion(connection, saved.withStatus(ACTIVE), 0);
            return null;
        });
        assertThatThrownBy(() -> transactions.inTransaction(connection -> {
            repository.updateWithVersion(connection, saved.withRole(ADMIN), 0);
            return null;
        })).isInstanceOf(ConcurrentModificationException.class);

        UserAccount updated = transactions.inTransaction(connection ->
                repository.findById(connection, saved.userId()).orElseThrow());
        assertThat(updated.accountStatus()).isEqualTo(ACTIVE);
        assertThat(updated.rowVersion()).isEqualTo(1);
    }

    @Test
    void searchesByKeywordRoleAndStatusWithPaging() {
        transactions.inTransaction(connection -> {
            repository.insert(connection, account("ALICE", TEACHER));
            repository.insert(connection, account("ALINA", TEACHER));
            repository.insert(connection, account("BOB", ADMIN));
            return null;
        });

        var page = transactions.inTransaction(connection -> repository.search(
                connection, new UserSearchQuery("ali", TEACHER, ACTIVE, 0, 1)));

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().getFirst().loginId()).isEqualTo("ALICE");
        assertThat(page.page()).isZero();
        assertThat(page.pageSize()).isEqualTo(1);
    }

    @Test
    void seedsActiveAdministratorWithApprovedTemporaryPassword() throws Exception {
        UserAccount administrator = transactions.inTransaction(connection ->
                repository.findByNormalizedLoginId(connection, "ADMIN").orElseThrow());

        assertThat(administrator.role()).isEqualTo(ADMIN);
        assertThat(administrator.accountStatus()).isEqualTo(ACTIVE);
        assertThat(administrator.mustChangePassword()).isTrue();
        assertThat(administrator.passwordIterations()).isEqualTo(120_000);
        byte[] salt = Base64.getDecoder().decode(administrator.passwordSalt());
        KeySpec spec = new PBEKeySpec("Admin1234".toCharArray(), salt, 120_000, 256);
        byte[] expectedHash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).getEncoded();
        assertThat(Base64.getDecoder().decode(administrator.passwordHash()))
                .containsExactly(expectedHash);
    }

    @Test
    void doesNotReportDuplicateUserIdAsDuplicateLoginId() {
        UserAccount first = account("FIRST", TEACHER);
        transactions.inTransaction(connection -> {
            repository.insert(connection, first);
            return null;
        });
        UserAccount sameId = account(first.userId(), "SECOND", TEACHER);

        assertThatThrownBy(() -> transactions.inTransaction(connection -> {
            repository.insert(connection, sameId);
            return null;
        })).isInstanceOf(PersistenceException.class)
                .isNotInstanceOf(DuplicateLoginIdException.class);
    }

    private static UserAccount account(String loginId, UserRole role) {
        return account(UUID.randomUUID().toString(), loginId, role);
    }

    private static UserAccount account(String userId, String loginId, UserRole role) {
        LocalDateTime now = LocalDateTime.of(2026, 8, 24, 10, 0);
        return new UserAccount(
                userId, loginId, "hash", "salt", 120_000,
                role, ACTIVE, false, 0, null, null, 0, now, now);
    }

    private static Path schemaPath() {
        return projectFile("schema", "010_user.sql");
    }

    private static Path seedPath() {
        return projectFile("seed", "010_roles_permissions.sql");
    }

    private static Path projectFile(String folder, String name) {
        Path fromModule = Path.of("..", "vcampus-database", folder, name);
        return Files.exists(fromModule) ? fromModule
                : Path.of("vcampus-database", folder, name);
    }

    private static void executeScript(java.sql.Connection connection, Path path)
            throws Exception {
        String sql = Files.readString(path);
        for (String statementSql : sql.split(";")) {
            if (!statementSql.isBlank()) {
                try (var statement = connection.createStatement()) {
                    statement.execute(statementSql.strip());
                }
            }
        }
    }
}
