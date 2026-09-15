package edu.seu.vcampus.server.user.service;

import edu.seu.vcampus.common.user.LoginCommand;
import edu.seu.vcampus.common.user.LoginResult;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.persistence.ConnectionProvider;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.routing.ClientContext;
import edu.seu.vcampus.server.user.domain.UserAccount;
import edu.seu.vcampus.server.user.repository.AccessAuditRepository;
import edu.seu.vcampus.server.user.repository.AccessUserRepository;
import edu.seu.vcampus.server.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.UUID;

import static edu.seu.vcampus.common.user.AccountStatus.ACTIVE;
import static edu.seu.vcampus.common.user.UserRole.STUDENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudentLoginByStudentNumberTest {
    private TransactionManager transactions;
    private UserService service;

    @BeforeEach
    void setupIsolatedDatabase() throws Exception {
        Path testData = Path.of("target", "test-data");
        Files.createDirectories(testData);
        String url = "jdbc:ucanaccess://" + testData.resolve(UUID.randomUUID() + ".accdb")
                + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true";
        ConnectionProvider provider = () -> DriverManager.getConnection(url);
        transactions = new TransactionManager(provider);
        try (Connection connection = provider.open()) {
            executeScript(connection, projectFile("schema", "010_user.sql"));
            executeScript(connection, projectFile("schema", "020_student.sql"));
            try (var statement = connection.createStatement()) {
                statement.execute("INSERT INTO tblRole (roleCode, roleName) VALUES ('STUDENT', '学生')");
                statement.execute("INSERT INTO tblDepartment (departmentId, departmentCode, departmentName, isActive, rowVersion) "
                        + "VALUES ('dept-01', 'CS', '计算机学院', TRUE, 0)");
                statement.execute("INSERT INTO tblMajor (majorId, departmentId, majorCode, majorName, isActive, rowVersion) "
                        + "VALUES ('major-01', 'dept-01', '801', '软件工程', TRUE, 0)");
                statement.execute("INSERT INTO tblClass (classId, majorId, classCode, className, enrollmentYear, classNumber, isActive, rowVersion) "
                        + "VALUES ('class-01', 'major-01', '801-2023-01', '801231班', 2023, 1, TRUE, 0)");
            }
        }
        UserRepository users = new AccessUserRepository();
        PasswordHasher hasher = new PasswordHasher();
        LocalDateTime now = LocalDateTime.now();

        UserAccount studentUser = new UserAccount(
                "u-stu-001", "213260001",
                "complexHash", "salt", 120_000,
                STUDENT, ACTIVE, false, 0, null, null, 0, now, now);

        transactions.inTransaction(connection -> {
            users.insert(connection, studentUser);
            try (var stmt = connection.prepareStatement("""
                    INSERT INTO tblStudent (studentId, userId, studentNumber, studentType,
                        studentName, gender, email, phone, classId, enrollmentDate,
                        studentStatus, rowVersion, createdAt, updatedAt)
                    VALUES (?, ?, ?, 'UNDERGRADUATE', '赵明轩', '男', 'test@seu.edu.cn',
                        '13900000001', 'class-01', #2023-09-01#, 'ACTIVE', 0, NOW(), NOW())
                    """)) {
                stmt.setString(1, "s-stu-001");
                stmt.setString(2, "u-stu-001");
                stmt.setString(3, "80123101");
                stmt.executeUpdate();
            }
            return null;
        });

        service = new UserServiceImpl(transactions, new StripedResourceLockManager(),
                users, new AccessAuditRepository(), hasher);
    }

    @Test
    void allowsStudentToLogInWithStudentNumber() {
        LoginResult result = service.login(
                new LoginCommand("80123101", "123456".toCharArray(), "client-1"),
                new ClientContext("conn-1", "127.0.0.1"));
        assertThat(result.user().userId()).isEqualTo("u-stu-001");
        assertThat(result.user().loginId()).isEqualTo("213260001");
        assertThat(result.user().role()).isEqualTo(STUDENT);
    }

    @Test
    void allowsStudentToLogInWithCampusCardNumber() {
        LoginResult result = service.login(
                new LoginCommand("213260001", "123456".toCharArray(), "client-1"),
                new ClientContext("conn-2", "127.0.0.1"));
        assertThat(result.user().userId()).isEqualTo("u-stu-001");
    }

    @Test
    void rejectsUnknownStudentNumber() {
        assertThatThrownBy(() -> service.login(
                new LoginCommand("99999999", "123456".toCharArray(), "client-1"),
                new ClientContext("conn-3", "127.0.0.1")))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("AUTH_INVALID_CREDENTIALS");
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
}
