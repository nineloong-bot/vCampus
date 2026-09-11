package edu.seu.vcampus.server.governance;

import edu.seu.vcampus.common.governance.AssignModuleAdministratorCommand;
import edu.seu.vcampus.common.governance.RemoveModuleAdministratorCommand;
import edu.seu.vcampus.common.governance.SwapModuleAdministratorsCommand;
import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.server.bootstrap.DatabaseInitializer;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.persistence.ConnectionProvider;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.security.SessionExpiredException;
import edu.seu.vcampus.server.security.UserIdentity;
import edu.seu.vcampus.server.session.SessionRegistry;
import edu.seu.vcampus.server.user.repository.AccessAuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Integration tests for dedicated-role module-administrator governance. */
class ModuleAdministrationServiceTest {
    private static final String SUPER = "00000000-0000-0000-0000-000000000001";
    private static final String STUDENT_ADMIN = "00000000-0000-0000-0000-000000000201";
    private static final String COURSE_ADMIN = "00000000-0000-0000-0000-000000000204";
    private static final String SHOP_ADMIN = "00000000-0000-0000-0000-000000000206";
    private static final String USER_ADMIN = "00000000-0000-0000-0000-000000000207";
    private static final String SECOND_USER_ADMIN =
            "00000000-0000-0000-0000-000000000301";
    private TransactionManager transactions;
    private SessionRegistry sessions;
    private ModuleAdministrationService service;

    @BeforeEach
    void createDatabase() throws Exception {
        Path database = Path.of("target", "test-data", UUID.randomUUID() + ".accdb");
        Files.createDirectories(database.getParent());
        DatabaseInitializer.main(new String[] {projectDirectory("schema").toString(),
                projectDirectory("seed").toString(), database.toString()});
        ConnectionProvider provider = () -> DriverManager.getConnection(
                "jdbc:ucanaccess://" + database + ";immediatelyReleaseResources=true");
        transactions = new TransactionManager(provider);
        transactions.inTransaction(connection -> {
            try (var statement = connection.prepareStatement("""
                    INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt,
                    passwordIterations, roleCode, accountStatus, mustChangePassword,
                    failedLoginCount, rowVersion, createdAt, updatedAt)
                    SELECT ?, 'SECOND_USER_ADMIN', passwordHash, passwordSalt,
                    passwordIterations, 'USER_ADMIN', 'ACTIVE', TRUE, 0, 0, NOW(), NOW()
                    FROM tblUser WHERE userId=?
                    """)) {
                statement.setString(1, SECOND_USER_ADMIN);
                statement.setString(2, USER_ADMIN);
                statement.executeUpdate();
            }
            return null;
        });
        sessions = new SessionRegistry();
        service = new ModuleAdministrationService(transactions,
                new StripedResourceLockManager(), new AccessModuleAdministrationRepository(),
                new AccessAuditRepository(), sessions);
    }

    @Test
    void listsFiveDedicatedModuleAdministratorRoles() {
        var snapshot = service.list();

        assertThat(snapshot.administrators()).extracting("moduleCode")
                .contains("STUDENT", "COURSE", "LIBRARY", "SHOP", "USER");
        assertThat(snapshot.administrators()).extracting("administratorRole")
                .contains(UserRole.STUDENT_ADMIN, UserRole.COURSE_ADMIN,
                        UserRole.LIBRARY_ADMIN, UserRole.SHOP_ADMIN,
                        UserRole.USER_ADMIN);
    }

    @Test
    void reassignsDedicatedRoleAndProtectsLastActiveAdministrator() {
        service.assign(SUPER, new AssignModuleAdministratorCommand(
                "COURSE", SECOND_USER_ADMIN, rowVersion(SECOND_USER_ADMIN)));

        assertThat(role(SECOND_USER_ADMIN)).isEqualTo(UserRole.COURSE_ADMIN);
        assertThat(activeCount(UserRole.USER_ADMIN)).isEqualTo(1);
        assertThat(activeCount(UserRole.COURSE_ADMIN)).isEqualTo(2);

        service.remove(SUPER, new RemoveModuleAdministratorCommand(
                "COURSE", COURSE_ADMIN, rowVersion(COURSE_ADMIN)));
        assertThat(status(COURSE_ADMIN)).isEqualTo(AccountStatus.DISABLED);

        assertThatThrownBy(() -> service.remove(SUPER,
                new RemoveModuleAdministratorCommand("SHOP", SHOP_ADMIN,
                        rowVersion(SHOP_ADMIN))))
                .hasMessage("GOVERNANCE_LAST_MODULE_ADMIN_PROTECTED");
        assertThat(status(SHOP_ADMIN)).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void swapsDedicatedRolesAtomicallyAndRevokesAffectedSessions() {
        String firstToken = token(STUDENT_ADMIN, UserRole.STUDENT_ADMIN);
        String secondToken = token(COURSE_ADMIN, UserRole.COURSE_ADMIN);

        service.swap(SUPER, new SwapModuleAdministratorsCommand(
                "STUDENT", STUDENT_ADMIN, rowVersion(STUDENT_ADMIN),
                "COURSE", COURSE_ADMIN, rowVersion(COURSE_ADMIN)));

        assertThat(role(STUDENT_ADMIN)).isEqualTo(UserRole.COURSE_ADMIN);
        assertThat(role(COURSE_ADMIN)).isEqualTo(UserRole.STUDENT_ADMIN);
        assertThatThrownBy(() -> sessions.requireSession(firstToken))
                .isInstanceOf(SessionExpiredException.class);
        assertThatThrownBy(() -> sessions.requireSession(secondToken))
                .isInstanceOf(SessionExpiredException.class);
    }

    @Test
    void superAdministratorCannotReceiveConcreteModuleRole() {
        assertThatThrownBy(() -> service.assign(SUPER,
                new AssignModuleAdministratorCommand("USER", SUPER, rowVersion(SUPER))))
                .hasMessage("COMMON_VALIDATION_FAILED");
        assertThat(role(SUPER)).isEqualTo(UserRole.SUPER_ADMIN);
    }

    private String token(String userId, UserRole role) {
        return sessions.create(new UserIdentity(userId, userId, role, AccountStatus.ACTIVE));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"assign", "remove", "swap"})
    void revokedSessionsCannotSurviveUntilAccountLocksAreReleased(String operation) {
        String token = token(SECOND_USER_ADMIN, UserRole.USER_ADMIN);
        var delegate = new StripedResourceLockManager();
        var checkedLocks = new edu.seu.vcampus.server.concurrency.ResourceLockManager() {
            @Override public <T> T withLocks(
                    java.util.List<edu.seu.vcampus.server.concurrency.ResourceKey> keys,
                    java.util.function.Supplier<T> action) {
                return delegate.withLocks(keys, () -> {
                    T result = action.get();
                    assertThatThrownBy(() -> sessions.requireSession(token))
                            .isInstanceOf(SessionExpiredException.class);
                    return result;
                });
            }
        };
        var subject = new ModuleAdministrationService(transactions, checkedLocks,
                new AccessModuleAdministrationRepository(), new AccessAuditRepository(), sessions);
        switch (operation) {
            case "assign" -> subject.assign(SUPER,
                    new AssignModuleAdministratorCommand("COURSE", SECOND_USER_ADMIN, 0));
            case "remove" -> subject.remove(SUPER,
                    new RemoveModuleAdministratorCommand("USER", SECOND_USER_ADMIN, 0));
            case "swap" -> subject.swap(SUPER, new SwapModuleAdministratorsCommand(
                    "USER", SECOND_USER_ADMIN, 0, "COURSE", COURSE_ADMIN, 0));
            default -> throw new AssertionError(operation);
        }
    }

    @Test void everyModuleProtectsItsFinalActiveAdministratorAndAuditsTheRejection() {
        service.remove(SUPER, new RemoveModuleAdministratorCommand("USER", SECOND_USER_ADMIN, 0));
        for (var administrator : service.list().administrators()) {
            if (administrator.accountStatus() != AccountStatus.ACTIVE) continue;
            assertThatThrownBy(() -> service.remove(SUPER, new RemoveModuleAdministratorCommand(
                    administrator.moduleCode(), administrator.userId(), administrator.rowVersion())))
                    .hasMessage("GOVERNANCE_LAST_MODULE_ADMIN_PROTECTED");
            assertThat(activeCount(administrator.administratorRole())).isEqualTo(1);
        }
        assertThat(auditCodes()).filteredOn("GOVERNANCE_LAST_MODULE_ADMIN_PROTECTED"::equals)
                .hasSize(5);
    }

    @Test
    void invalidGovernanceWritesAreAuditedWithStableCodes() {
        assertThatThrownBy(() -> service.assign(SUPER,
                new AssignModuleAdministratorCommand("INVALID", USER_ADMIN, 0)))
                .hasMessage("COMMON_VALIDATION_FAILED");
        assertThatThrownBy(() -> service.remove(SUPER,
                new RemoveModuleAdministratorCommand("INVALID", USER_ADMIN, 0)))
                .hasMessage("COMMON_VALIDATION_FAILED");
        assertThatThrownBy(() -> service.swap(SUPER,
                new SwapModuleAdministratorsCommand("USER", USER_ADMIN, 0,
                        "USER", USER_ADMIN, 0)))
                .hasMessage("COMMON_VALIDATION_FAILED");
        assertThat(auditCodes()).containsExactly(
                "COMMON_VALIDATION_FAILED", "COMMON_VALIDATION_FAILED",
                "COMMON_VALIDATION_FAILED");
    }

    @Test
    void rejectionAuditPreservesAuthorizationCodeAndSanitizesInternalMessages() {
        service.auditRejected(null, "GOVERNANCE_MODULE_ADMIN_ASSIGN", "USER",
                USER_ADMIN, new edu.seu.vcampus.server.security.ForbiddenException());
        service.auditRejected(SUPER, "GOVERNANCE_MODULE_ADMIN_REMOVE", "USER",
                USER_ADMIN, new IllegalStateException("COMMON_SECRET SQL password token"));
        assertThat(auditCodes()).containsExactlyInAnyOrder(
                "AUTH_FORBIDDEN", "COMMON_INTERNAL_ERROR");
    }

    private java.util.List<String> auditCodes() {
        return transactions.inTransaction(connection -> {
            try (var statement = connection.prepareStatement(
                    "SELECT resultCode FROM tblAuditLog WHERE actionCode LIKE 'GOVERNANCE_%'");
                 var result = statement.executeQuery()) {
                java.util.List<String> codes = new java.util.ArrayList<>();
                while (result.next()) codes.add(result.getString(1));
                return codes;
            }
        });
    }

    private UserRole role(String userId) {
        return UserRole.valueOf(column(userId, "roleCode"));
    }

    private AccountStatus status(String userId) {
        return AccountStatus.valueOf(column(userId, "accountStatus"));
    }

    private String column(String userId, String column) {
        return transactions.inTransaction(connection -> {
            try (var statement = connection.prepareStatement(
                    "SELECT " + column + " FROM tblUser WHERE userId=?")) {
                statement.setString(1, userId);
                try (var result = statement.executeQuery()) {
                    if (!result.next()) throw new AssertionError("missing user");
                    return result.getString(1);
                }
            }
        });
    }

    private long rowVersion(String userId) {
        return Long.parseLong(column(userId, "rowVersion"));
    }

    private long activeCount(UserRole role) {
        return transactions.inTransaction(connection -> {
            try (var statement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM tblUser WHERE roleCode=? AND accountStatus='ACTIVE'")) {
                statement.setString(1, role.name());
                try (var result = statement.executeQuery()) {
                    result.next();
                    return result.getLong(1);
                }
            }
        });
    }

    private static Path projectDirectory(String child) {
        Path current = Path.of("").toAbsolutePath();
        Path database = current.getFileName().toString().equals("vcampus-server")
                ? current.resolve("..").resolve("vcampus-database")
                : current.resolve("vcampus-database");
        return database.resolve(child).normalize();
    }
}
