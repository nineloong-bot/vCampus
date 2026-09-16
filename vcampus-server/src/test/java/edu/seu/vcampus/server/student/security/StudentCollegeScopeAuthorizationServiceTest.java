package edu.seu.vcampus.server.student.security;

import edu.seu.vcampus.server.persistence.ConnectionProvider;
import edu.seu.vcampus.server.persistence.TransactionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudentCollegeScopeAuthorizationServiceTest {
    private static final String CS_ADMIN="user-cse-admin";
    private static final String MATH_ADMIN="user-math-admin";
    private static final String CS_STUDENT="student-2024-001";
    private TransactionManager transactions;
    private StudentCollegeScopeAuthorizationService authorization;

    @BeforeEach void setup() throws Exception {
        Path database=Path.of("target","test-data",UUID.randomUUID()+".accdb");
        Files.createDirectories(database.getParent());
        Files.copy(distributionDatabase(), database);
        ConnectionProvider provider=()->DriverManager.getConnection("jdbc:ucanaccess://"+database+";immediatelyReleaseResources=true");
        transactions=new TransactionManager(provider);
        authorization=new StudentCollegeScopeAuthorizationService(transactions);
    }

    @Test void checksTheStudentsCurrentCollegeOnEveryRequest(){
        assertThatCode(()->authorization.requireStudentAccess(CS_ADMIN,CS_STUDENT)).doesNotThrowAnyException();
        assertThatThrownBy(()->authorization.requireStudentAccess(MATH_ADMIN,CS_STUDENT))
                .hasMessage("COMMON_FORBIDDEN");
        transactions.inTransaction(c->{try(var s=c.prepareStatement("UPDATE tblStudent SET classId='class-math-2024' WHERE studentId=?")){s.setString(1,CS_STUDENT);s.executeUpdate();}return null;});
        assertThatThrownBy(()->authorization.requireStudentAccess(CS_ADMIN,CS_STUDENT))
                .hasMessage("COMMON_FORBIDDEN");
        assertThatCode(()->authorization.requireStudentAccess(MATH_ADMIN,CS_STUDENT)).doesNotThrowAnyException();
    }

    @Test void superAndModuleAdministratorsCannotBypassCollegeScope(){
        assertThatThrownBy(()->authorization.requireStudentAccess("user-super-admin",CS_STUDENT)).hasMessage("COMMON_FORBIDDEN");
        assertThatThrownBy(()->authorization.requireStudentAccess("user-student-admin",CS_STUDENT)).hasMessage("COMMON_FORBIDDEN");
    }

    @Test void resolvesExactlyOneLiveCollegeBinding(){
        org.assertj.core.api.Assertions.assertThat(
                authorization.requireActiveDepartment(CS_ADMIN))
                .isEqualTo("dept-cse");
        transactions.inTransaction(c->{try(var s=c.prepareStatement(
                "UPDATE tblStudentCollegeAdministrator SET isActive=FALSE WHERE userId=?")){
            s.setString(1,CS_ADMIN);s.executeUpdate();}return null;});
        assertThatThrownBy(()->authorization.requireActiveDepartment(CS_ADMIN))
                .hasMessage("COMMON_FORBIDDEN");
    }

    @Test void transactionAwareStudentCheckUsesTrustedDepartment(){
        transactions.inTransaction(connection->{
            assertThatCode(()->authorization.requireStudentAccess(connection,
                    "dept-cse",CS_STUDENT))
                    .doesNotThrowAnyException();
            assertThatThrownBy(()->authorization.requireStudentAccess(connection,
                    "dept-math",CS_STUDENT))
                    .hasMessage("COMMON_FORBIDDEN");
            return null;
        });
    }

    @Test void validatesMajorClassAndPlanAgainstTrustedDepartment(){
        transactions.inTransaction(connection->{
            assertThatCode(()->authorization.requireMajorAccess(connection,
                    "dept-cse", "major-cs")).doesNotThrowAnyException();
            assertThatCode(()->authorization.requireClassAccess(connection,
                    "dept-cse", "class-cs-2024")).doesNotThrowAnyException();
            assertThatCode(()->authorization.requirePlanAccess(connection,
                    "dept-cse", "plan-cs-2024")).doesNotThrowAnyException();
            assertThatThrownBy(()->authorization.requireMajorAccess(connection,
                    "dept-cse", "major-math")).hasMessage("COMMON_FORBIDDEN");
            assertThatThrownBy(()->authorization.requireClassAccess(connection,
                    "dept-cse", "class-math-2024")).hasMessage("COMMON_FORBIDDEN");
            return null;
        });
    }
    private static Path distributionDatabase(){
        Path current=Path.of("").toAbsolutePath();
        Path root=current.getFileName().toString().equals("vcampus-server")?current.resolve(".."):current;
        return root.resolve("vcampus-distribution/data/vCampus.accdb").normalize();
    }
}
