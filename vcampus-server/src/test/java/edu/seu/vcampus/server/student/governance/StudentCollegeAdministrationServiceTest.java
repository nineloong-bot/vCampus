package edu.seu.vcampus.server.student.governance;

import edu.seu.vcampus.common.student.governance.AssignStudentCollegeAdministratorCommand;
import edu.seu.vcampus.common.student.governance.DeactivateStudentCollegeAdministratorCommand;
import edu.seu.vcampus.common.student.governance.TransferStudentCollegeAdministratorCommand;
import edu.seu.vcampus.server.bootstrap.DatabaseInitializer;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.persistence.ConnectionProvider;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.session.SessionRegistry;
import edu.seu.vcampus.server.security.SessionExpiredException;
import edu.seu.vcampus.server.security.UserIdentity;
import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.server.user.repository.AccessAuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudentCollegeAdministrationServiceTest {
    private static final String ACTOR = "00000000-0000-0000-0000-000000000201";
    private static final String CS = "bulk-dept-01";
    private static final String MATH = "bulk-dept-02";
    private static final String CS_ADMIN = "00000000-0000-0000-0000-000000000202";
    private static final String MATH_ADMIN = "00000000-0000-0000-0000-000000000203";
    private static final String EXTRA = "00000000-0000-0000-0000-000000000302";
    private TransactionManager transactions;
    private StudentCollegeAdministrationService service;
    private SessionRegistry sessions;

    @BeforeEach void setup() throws Exception {
        Path database = Path.of("target", "test-data", UUID.randomUUID() + ".accdb");
        Files.createDirectories(database.getParent());
        DatabaseInitializer.main(new String[]{directory("schema").toString(), directory("seed").toString(), database.toString()});
        ConnectionProvider provider = () -> DriverManager.getConnection("jdbc:ucanaccess://" + database + ";immediatelyReleaseResources=true");
        transactions = new TransactionManager(provider);
        transactions.inTransaction(connection -> {
            try (var statement = connection.prepareStatement("""
                    INSERT INTO tblUser (userId,loginId,passwordHash,passwordSalt,passwordIterations,
                    roleCode,accountStatus,mustChangePassword,failedLoginCount,rowVersion,createdAt,updatedAt)
                    SELECT ?,'EXTRA_COLLEGE_ADMIN',passwordHash,passwordSalt,passwordIterations,
                    'COLLEGE_ADMIN','ACTIVE',TRUE,0,0,NOW(),NOW() FROM tblUser WHERE userId=?
                    """)) { statement.setString(1, EXTRA); statement.setString(2, CS_ADMIN); statement.executeUpdate(); }
            return null;
        });
        sessions = new SessionRegistry();
        service = new StudentCollegeAdministrationService(transactions,
                new StripedResourceLockManager(), new AccessStudentCollegeAdministrationRepository(),
                new AccessAuditRepository(), sessions);
    }

    @Test void enforcesUniqueCollegeAndProtectsLastAdministrator() {
        service.assign(ACTOR, new AssignStudentCollegeAdministratorCommand(CS, EXTRA, 0));
        assertThat(active(CS, EXTRA)).isTrue();
        assertThatThrownBy(() -> service.assign(ACTOR,
                new AssignStudentCollegeAdministratorCommand(MATH, EXTRA, 0)))
                .hasMessage("COMMON_VALIDATION_FAILED");
        assertThatThrownBy(() -> service.deactivate(ACTOR,
                new DeactivateStudentCollegeAdministratorCommand(MATH, MATH_ADMIN, 0)))
                .hasMessage("STUDENT_LAST_COLLEGE_ADMIN_PROTECTED");
        assertThat(active(MATH, MATH_ADMIN)).isTrue();
    }

    @Test
    void listsSanitizedAdministratorsAndAssignableDepartments() {
        var snapshot = service.list();

        assertThat(snapshot.administrators()).extracting("userId")
                .contains(CS_ADMIN, MATH_ADMIN, EXTRA);
        assertThat(snapshot.administrators()).allSatisfy(administrator ->
                assertThat(administrator.loginId()).isNotBlank());
        assertThat(snapshot.departments()).extracting("departmentId")
                .contains(CS, MATH);
    }

    @Test
    void assignmentRevokesExistingAdministratorSessions() {
        String token = sessions.create(new UserIdentity(EXTRA, "EXTRA_COLLEGE_ADMIN",
                UserRole.COLLEGE_ADMIN, AccountStatus.ACTIVE));

        service.assign(ACTOR, new AssignStudentCollegeAdministratorCommand(CS, EXTRA, 0));

        assertThatThrownBy(() -> sessions.requireSession(token))
                .isInstanceOf(SessionExpiredException.class);
    }

    @Test void transfersAtomicallyWithoutLeavingSourceCollegeUnmanaged() {
        service.assign(ACTOR, new AssignStudentCollegeAdministratorCommand(CS, EXTRA, 0));
        service.transfer(ACTOR, new TransferStudentCollegeAdministratorCommand(
                EXTRA, CS, MATH, version(CS, EXTRA), 0));
        assertThat(active(CS, EXTRA)).isFalse();
        assertThat(active(CS, CS_ADMIN)).isTrue();
        assertThat(active(MATH, EXTRA)).isTrue();
    }

    @Test
    void transferRejectsSameSourceAndTargetDepartment() {
        assertThatThrownBy(() -> service.transfer(ACTOR, new TransferStudentCollegeAdministratorCommand(
                CS_ADMIN, CS, CS, 0, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("调动目标学院不能与当前学院相同");
    }

    @Test
    void provisionsNewCollegeAdministratorWithoutAssignment() {
        var create = new edu.seu.vcampus.common.student.governance.CreateCollegeAdministratorCommand(
                "NEW_UNASSIGNED_ADMIN", "Pass1234", null);
        service.createAdministrator(ACTOR, create);

        var snapshot = service.list();
        var found = snapshot.administrators().stream()
                .filter(a -> "NEW_UNASSIGNED_ADMIN".equalsIgnoreCase(a.loginId()))
                .findFirst();
        assertThat(found).isPresent();
        assertThat(found.get().assigned()).isFalse();
        assertThat(found.get().departmentId()).isNull();

        // Assign to CS
        service.assign(ACTOR, new AssignStudentCollegeAdministratorCommand(CS, found.get().userId(), 0));
        assertThat(active(CS, found.get().userId())).isTrue();
    }

    @Test
    void provisionsNewCollegeAdministratorWithDirectAssignment() {
        var create = new edu.seu.vcampus.common.student.governance.CreateCollegeAdministratorCommand(
                "NEW_CS_ADMIN", "Pass1234", CS);
        service.createAdministrator(ACTOR, create);

        var snapshot = service.list();
        var found = snapshot.administrators().stream()
                .filter(a -> "NEW_CS_ADMIN".equalsIgnoreCase(a.loginId()))
                .findFirst();
        assertThat(found).isPresent();
        assertThat(found.get().assigned()).isTrue();
        assertThat(found.get().departmentId()).isEqualTo(CS);
        assertThat(active(CS, found.get().userId())).isTrue();
    }

    @Test
    void provisionsAdministratorRejectsDuplicateUsername() {
        var create = new edu.seu.vcampus.common.student.governance.CreateCollegeAdministratorCommand(
                "CS_COLLEGE_ADMIN", "Pass1234", null);
        assertThatThrownBy(() -> service.createAdministrator(ACTOR, create))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("USER_LOGIN_ID_EXISTS");
    }

    private boolean active(String department, String user) {
        return transactions.inTransaction(c -> {
            try (var s=c.prepareStatement("SELECT isActive FROM tblStudentCollegeAdministrator WHERE departmentId=? AND userId=?")) {
                s.setString(1, department); s.setString(2, user); try(var r=s.executeQuery()){return r.next()&&r.getBoolean(1);} }
        });
    }
    private long version(String department, String user) {
        return transactions.inTransaction(c -> {
            try(var s=c.prepareStatement("SELECT rowVersion FROM tblStudentCollegeAdministrator WHERE departmentId=? AND userId=?")){
                s.setString(1,department);s.setString(2,user);try(var r=s.executeQuery()){if(!r.next())throw new AssertionError();return r.getLong(1);}}
        });
    }
    private static Path directory(String child) {
        Path current=Path.of("").toAbsolutePath();
        return (current.getFileName().toString().equals("vcampus-server")?current.resolve("../vcampus-database"):current.resolve("vcampus-database")).resolve(child).normalize();
    }
}
