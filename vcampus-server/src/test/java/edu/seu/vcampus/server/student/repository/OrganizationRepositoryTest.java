package edu.seu.vcampus.server.student.repository;

import edu.seu.vcampus.server.persistence.ConnectionProvider;
import edu.seu.vcampus.server.student.domain.Department;
import edu.seu.vcampus.server.student.domain.Major;
import edu.seu.vcampus.server.student.domain.StudentClass;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrganizationRepositoryTest {
    private ConnectionProvider provider;
    private OrganizationRepository repository;

    @BeforeEach
    void createDatabase() throws Exception {
        Path testData = Path.of("target", "test-data");
        Files.createDirectories(testData);
        String url = "jdbc:ucanaccess://" + testData.resolve(UUID.randomUUID() + ".accdb")
                + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true";
        provider = () -> DriverManager.getConnection(url);
        repository = new AccessOrganizationRepository();
        try (Connection connection = provider.open()) {
            executeSchema(connection, Path.of("..", "vcampus-database", "schema", "020_student.sql"));
        }
    }

    @Test
    void storesHierarchyAndListsOnlyActiveChildren() throws Exception {
        try (Connection connection = provider.open()) {
            Department engineering = new Department("dep-eng", "ENG", "工学院", true, 0);
            repository.insertDepartment(connection, engineering);
            repository.insertMajor(connection,
                    new Major("major-cs", engineering.departmentId(), "090", "计算机科学", true, 0));
            repository.insertMajor(connection,
                    new Major("major-old", engineering.departmentId(), "091", "停用专业", false, 0));
            repository.insertClass(connection,
                    new StudentClass("class-1", "major-cs", "090-2024-1", "计算机一班",
                            2024, 1, true, 0));

            assertThat(repository.findDepartment(connection, "dep-eng")).contains(engineering);
            assertThat(repository.listActiveMajors(connection, "dep-eng"))
                    .extracting(Major::majorCode)
                    .containsExactly("090");
            assertThat(repository.listActiveClasses(connection, "major-cs"))
                    .extracting(StudentClass::classNumber)
                    .containsExactly(1);
        }
    }

    @Test
    void rejectsClassWhoseMajorDoesNotBelongToDepartment() throws Exception {
        try (Connection connection = provider.open()) {
            repository.insertDepartment(connection,
                    new Department("dep-cs", "CS", "计算机学院", true, 0));
            repository.insertDepartment(connection,
                    new Department("dep-law", "LAW", "法学院", true, 0));
            repository.insertMajor(connection,
                    new Major("major-law", "dep-law", "120", "法学", true, 0));
            repository.insertClass(connection,
                    new StudentClass("class-law", "major-law", "120-2024-1", "法学一班",
                            2024, 1, true, 0));

            assertThat(repository.classBelongsTo(
                    connection, "class-law", "major-law", "dep-cs")).isFalse();
            assertThat(repository.classBelongsTo(
                    connection, "class-law", "major-law", "dep-law")).isTrue();
        }
    }

    @Test
    void refusesToDeactivateParentWithActiveChildren() throws Exception {
        try (Connection connection = provider.open()) {
            repository.insertDepartment(connection,
                    new Department("dep-eng", "ENG", "工学院", true, 0));
            repository.insertMajor(connection,
                    new Major("major-cs", "dep-eng", "090", "计算机科学", true, 0));
            repository.insertClass(connection,
                    new StudentClass("class-1", "major-cs", "090-2024-1", "计算机一班",
                            2024, 1, true, 0));

            assertThatThrownBy(() -> repository.deactivateDepartment(connection, "dep-eng", 0))
                    .isInstanceOf(OrganizationHierarchyException.class);
            assertThatThrownBy(() -> repository.deactivateMajor(connection, "major-cs", 0))
                    .isInstanceOf(OrganizationHierarchyException.class);
            assertThat(repository.findDepartment(connection, "dep-eng").orElseThrow().active())
                    .isTrue();
        }
    }

    @Test
    void enforcesMajorCodeAndClassNumberUniqueness() throws Exception {
        try (Connection connection = provider.open()) {
            repository.insertDepartment(connection,
                    new Department("dep-eng", "ENG", "工学院", true, 0));
            repository.insertMajor(connection,
                    new Major("major-cs", "dep-eng", "09J", "计算机拔尖班", true, 0));
            repository.insertClass(connection,
                    new StudentClass("class-1", "major-cs", "09J-2024-1", "拔尖一班",
                            2024, 1, true, 0));

            assertThatThrownBy(() -> repository.insertMajor(connection,
                    new Major("major-duplicate", "dep-eng", "09J", "重复代码", true, 0)))
                    .isInstanceOf(OrganizationPersistenceException.class);
            assertThatThrownBy(() -> repository.insertClass(connection,
                    new StudentClass("class-duplicate", "major-cs", "09J-2024-X", "重复班号",
                            2024, 1, true, 0)))
                    .isInstanceOf(OrganizationPersistenceException.class);
        }
    }

    @Test
    void creatingClassInitializesItsStudentNumberSequence() throws Exception {
        try (Connection connection = provider.open()) {
            repository.insertDepartment(connection,
                    new Department("dep-eng", "ENG", "工学院", true, 0));
            repository.insertMajor(connection,
                    new Major("major-honors", "dep-eng", "09J", "计算机拔尖班", true, 0));
            repository.insertClass(connection,
                    new StudentClass("class-1", "major-honors", "09J-2024-1", "拔尖一班",
                            2024, 1, true, 0));

            try (var statement = connection.prepareStatement(
                    "SELECT currentValue, maxValue FROM tblNumberSequence WHERE sequenceKey = ?")) {
                statement.setString(1, "STUDENT_NUMBER:09J:24:1");
                try (var result = statement.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getInt("currentValue")).isZero();
                    assertThat(result.getInt("maxValue")).isEqualTo(99);
                }
            }
        }
    }

    private static void executeSchema(Connection connection, Path schema) throws Exception {
        String sql = Files.readString(schema);
        for (String statementSql : sql.split(";")) {
            String statementText = statementSql.trim();
            if (!statementText.isEmpty()) {
                try (var statement = connection.createStatement()) {
                    statement.execute(statementText);
                }
            }
        }
    }
}
