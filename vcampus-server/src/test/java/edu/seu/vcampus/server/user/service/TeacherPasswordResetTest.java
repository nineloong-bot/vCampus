package edu.seu.vcampus.server.user.service;

import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.common.user.ChangePasswordCommand;
import edu.seu.vcampus.common.user.LoginCommand;
import edu.seu.vcampus.common.user.ResetTeacherPasswordCommand;
import edu.seu.vcampus.common.user.UserRole;
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
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ConcurrentModificationException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TeacherPasswordResetTest {
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
                Clock.systemUTC());
    }

    @Test
    void administratorInitializesOnlyTeacherAndForcesOneRestrictedRelogin() {
        UserAccount teacher = account("RESET_TEACHER", UserRole.TEACHER);
        insert(teacher);
        String oldToken = login(teacher.loginId(), "Pass1234").sessionToken();
        UserAccount beforeReset = find(teacher.userId());

        var view = service.resetTeacherPassword(ADMIN_ID,
                new ResetTeacherPasswordCommand(
                        teacher.userId(), beforeReset.rowVersion()), ADMIN_CONTEXT);

        assertThat(view.mustChangePassword()).isTrue();
        assertThat(view.toString()).doesNotContain(
                "12345678", "password", "salt", "token");
        assertThatThrownBy(() -> sessions.requireSession(oldToken))
                .hasMessage("AUTH_SESSION_REVOKED_PASSWORD_RESET");
        assertThatThrownBy(() -> login(teacher.loginId(), "Pass1234"))
                .isInstanceOf(InvalidCredentialsException.class);

        var restricted = login(teacher.loginId(), "12345678");
        assertThat(restricted.mustChangePassword()).isTrue();
        service.changePassword(restricted.sessionToken(), new ChangePasswordCommand(
                "12345678".toCharArray(), "TeacherNew9".toCharArray()));
        assertThatThrownBy(() -> sessions.requireSession(restricted.sessionToken()))
                .isInstanceOf(SessionExpiredException.class);
        assertThat(login(teacher.loginId(), "TeacherNew9").mustChangePassword()).isFalse();

        UserAccount stored = find(teacher.userId());
        assertThat(stored.failedLoginCount()).isZero();
        assertThat(stored.lockedUntil()).isNull();
        assertThat(stored.mustChangePassword()).isFalse();
        assertAudit("USER_PASSWORD_RESET", "SUCCESS", ADMIN_ID, teacher.userId());
    }

    @Test
    void rejectsStudentAdministratorMissingAndStaleTeacherWithoutMutation() {
        UserAccount student = account("RESET_STUDENT_REJECTED", UserRole.STUDENT);
        UserAccount administrator = account("RESET_ADMIN_REJECTED", UserRole.ADMIN);
        UserAccount teacher = account("RESET_STALE_TEACHER", UserRole.TEACHER);
        insert(student);
        insert(administrator);
        insert(teacher);

        assertValidationFailure(student);
        assertValidationFailure(administrator);
        assertThatThrownBy(() -> service.resetTeacherPassword(ADMIN_ID,
                new ResetTeacherPasswordCommand("missing", 0), ADMIN_CONTEXT))
                .hasMessage("USER_NOT_FOUND");
        assertThatThrownBy(() -> service.resetTeacherPassword(ADMIN_ID,
                new ResetTeacherPasswordCommand(teacher.userId(), 9), ADMIN_CONTEXT))
                .isInstanceOf(ConcurrentModificationException.class);

        assertThat(find(student.userId()).rowVersion()).isZero();
        assertThat(find(administrator.userId()).rowVersion()).isZero();
        assertThat(find(teacher.userId()).rowVersion()).isZero();
    }

    private void assertValidationFailure(UserAccount target) {
        assertThatThrownBy(() -> service.resetTeacherPassword(ADMIN_ID,
                new ResetTeacherPasswordCommand(target.userId(), target.rowVersion()),
                ADMIN_CONTEXT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("COMMON_VALIDATION_FAILED");
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
        LocalDateTime now = LocalDateTime.of(2026, 9, 2, 9, 0);
        return new UserAccount(UUID.randomUUID().toString(), loginId, password.hash(),
                password.salt(), password.iterations(), role, AccountStatus.ACTIVE,
                false, 0, null, null, 0, now, now);
    }

    private static Path projectFile(String folder, String name) {
        Path fromModule = Path.of("..", "vcampus-database", folder, name);
        return Files.exists(fromModule) ? fromModule
                : Path.of("vcampus-database", folder, name);
    }

    private static void execute(java.sql.Connection connection, Path script) throws Exception {
        for (String sql : Files.readString(script).split(";")) {
            if (!sql.isBlank()) try (var statement = connection.createStatement()) {
                statement.execute(sql.strip());
            }
        }
    }
}
