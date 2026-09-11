package edu.seu.vcampus.server.student.governance;

import edu.seu.vcampus.common.student.governance.AssignStudentCollegeAdministratorCommand;
import edu.seu.vcampus.common.student.governance.DeactivateStudentCollegeAdministratorCommand;
import edu.seu.vcampus.common.student.governance.TransferStudentCollegeAdministratorCommand;
import edu.seu.vcampus.server.bootstrap.DatabaseInitializer;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.persistence.ConnectionProvider;
import edu.seu.vcampus.server.persistence.TransactionManager;
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

class StudentCollegeAdministrationServiceTest {
    private static final String ACTOR = "00000000-0000-0000-0000-000000000201";
    private static final String CS = "00000000-0000-0000-0000-000000000101";
    private static final String MATH = "00000000-0000-0000-0000-000000000111";
    private static final String CS_ADMIN = "00000000-0000-0000-0000-000000000202";
    private static final String MATH_ADMIN = "00000000-0000-0000-0000-000000000203";
    private static final String EXTRA = "00000000-0000-0000-0000-000000000302";
    private TransactionManager transactions;
    private StudentCollegeAdministrationService service;

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
        service = new StudentCollegeAdministrationService(transactions,
                new StripedResourceLockManager(), new AccessStudentCollegeAdministrationRepository(),
                new AccessAuditRepository(), new SessionRegistry());
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

    @Test void transfersAtomicallyWithoutLeavingSourceCollegeUnmanaged() {
        service.assign(ACTOR, new AssignStudentCollegeAdministratorCommand(CS, EXTRA, 0));
        service.transfer(ACTOR, new TransferStudentCollegeAdministratorCommand(
                EXTRA, CS, MATH, version(CS, EXTRA), 0));
        assertThat(active(CS, EXTRA)).isFalse();
        assertThat(active(CS, CS_ADMIN)).isTrue();
        assertThat(active(MATH, EXTRA)).isTrue();
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
