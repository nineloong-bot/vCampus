package edu.seu.vcampus.server.student.security;

import edu.seu.vcampus.server.bootstrap.DatabaseInitializer;
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
    private static final String CS_ADMIN="00000000-0000-0000-0000-000000000202";
    private static final String MATH_ADMIN="00000000-0000-0000-0000-000000000203";
    private static final String CS_STUDENT="00000000-0000-0000-0000-000000000104";
    private TransactionManager transactions;
    private StudentCollegeScopeAuthorizationService authorization;

    @BeforeEach void setup() throws Exception {
        Path database=Path.of("target","test-data",UUID.randomUUID()+".accdb");
        Files.createDirectories(database.getParent());
        DatabaseInitializer.main(new String[]{directory("schema").toString(),directory("seed").toString(),database.toString()});
        ConnectionProvider provider=()->DriverManager.getConnection("jdbc:ucanaccess://"+database+";immediatelyReleaseResources=true");
        transactions=new TransactionManager(provider);
        authorization=new StudentCollegeScopeAuthorizationService(transactions);
    }

    @Test void checksTheStudentsCurrentCollegeOnEveryRequest(){
        assertThatCode(()->authorization.requireStudentAccess(CS_ADMIN,CS_STUDENT)).doesNotThrowAnyException();
        assertThatThrownBy(()->authorization.requireStudentAccess(MATH_ADMIN,CS_STUDENT))
                .hasMessage("COMMON_FORBIDDEN");
        transactions.inTransaction(c->{try(var s=c.prepareStatement("UPDATE tblStudent SET classId='00000000-0000-0000-0000-000000000115' WHERE studentId=?")){s.setString(1,CS_STUDENT);s.executeUpdate();}return null;});
        assertThatThrownBy(()->authorization.requireStudentAccess(CS_ADMIN,CS_STUDENT))
                .hasMessage("COMMON_FORBIDDEN");
        assertThatCode(()->authorization.requireStudentAccess(MATH_ADMIN,CS_STUDENT)).doesNotThrowAnyException();
    }

    @Test void superAndModuleAdministratorsCannotBypassCollegeScope(){
        assertThatThrownBy(()->authorization.requireStudentAccess("00000000-0000-0000-0000-000000000001",CS_STUDENT)).hasMessage("COMMON_FORBIDDEN");
        assertThatThrownBy(()->authorization.requireStudentAccess("00000000-0000-0000-0000-000000000201",CS_STUDENT)).hasMessage("COMMON_FORBIDDEN");
    }
    private static Path directory(String child){Path current=Path.of("").toAbsolutePath();return(current.getFileName().toString().equals("vcampus-server")?current.resolve("../vcampus-database"):current.resolve("vcampus-database")).resolve(child).normalize();}
}
