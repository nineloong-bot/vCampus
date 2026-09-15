package edu.seu.vcampus.server.student.service;

import edu.seu.vcampus.common.student.StudentSearchQuery;
import edu.seu.vcampus.common.student.StudentStatus;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.student.repository.AccessOrganizationRepository;
import edu.seu.vcampus.server.student.repository.StudentChangeRepository;
import edu.seu.vcampus.server.student.repository.StudentRepository;
import edu.seu.vcampus.server.student.support.StudentAccessTestDatabase;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the student search privacy contract. */
class StudentSearchPrivacyTest {
    @Test
    void teacherSearchReturnsRestrictedSummaryWithoutContactFields() throws Exception {
        var database = new StudentAccessTestDatabase();
        var repository = new StudentRepository();
        database.transactions().inTransaction(connection -> {
            StudentFixtures.insertOrganization(connection, new AccessOrganizationRepository());
            repository.insert(connection, StudentProfileUpdateTest.student(StudentStatus.ACTIVE));
            return null;
        });
        StudentService service = new StudentServiceImpl(database.transactions(),
                new StripedResourceLockManager(), repository, new StudentChangeRepository(),
                new AccessOrganizationRepository(),
                StudentFixtures.userQueries("user-1", "213240001"), "teacher-1");

        var page = service.searchStudents(new StudentSearchQuery("张三", null, null,
                null, StudentStatus.ACTIVE, 1, 20));

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items().getFirst().campusCardNumber()).isEqualTo("213240001");
        assertThat(Arrays.stream(page.items().getFirst().getClass().getRecordComponents())
                .map(RecordComponent::getName)).doesNotContain("email", "phone");

        var outsideDepartment = service.searchStudents(new StudentSearchQuery(null,
                "department-else", null, null, null, 1, 20));
        assertThat(outsideDepartment.total()).isZero();
    }

    @Test
    void trustedCollegeScopeOverridesForgedDepartmentFilter() throws Exception {
        var database = new StudentAccessTestDatabase();
        var repository = new StudentRepository();
        var organizations = new AccessOrganizationRepository();
        database.transactions().inTransaction(connection -> {
            StudentFixtures.insertOrganization(connection, organizations);
            organizations.insertDepartment(connection, new edu.seu.vcampus.server.student.domain.Department(
                    "department-2", "SE", "软件学院", true, 0));
            organizations.insertMajor(connection, new edu.seu.vcampus.server.student.domain.Major(
                    "major-2", "department-2", "091", "软件工程", null, true, 0));
            organizations.insertClass(connection, new edu.seu.vcampus.server.student.domain.StudentClass(
                    "class-2", "major-2", "091-24-1", "软工24-1", 2024, 1, true, 0));
            repository.insert(connection, StudentProfileUpdateTest.student(StudentStatus.ACTIVE));
            repository.insert(connection, new edu.seu.vcampus.server.student.domain.Student(
                    "student-2", "user-2", "09124101",
                    edu.seu.vcampus.common.student.StudentType.UNDERGRADUATE, "李四", "男",
                    null, null, "major-2", "class-2", LocalDate.of(2024, 9, 1),
                    StudentStatus.ACTIVE, 0, Instant.now(), Instant.now()));
            return null;
        });
        var users = new edu.seu.vcampus.server.user.service.UserQueryPort() {
            @Override public java.util.Optional<edu.seu.vcampus.server.security.UserIdentity> findActiveUser(String id) { return findByUserId(id); }
            @Override public java.util.Optional<edu.seu.vcampus.server.security.UserIdentity> findByUserId(String id) {
                String login = "user-1".equals(id) ? "213240001" : "user-2".equals(id) ? "213240002" : null;
                return login == null ? java.util.Optional.empty() : java.util.Optional.of(
                        new edu.seu.vcampus.server.security.UserIdentity(id, login,
                                edu.seu.vcampus.common.user.UserRole.STUDENT,
                                edu.seu.vcampus.common.user.AccountStatus.ACTIVE));
            }
            @Override public java.util.Optional<edu.seu.vcampus.server.security.UserIdentity> findByLoginId(String id) { return java.util.Optional.empty(); }
            @Override public boolean hasRole(String id, edu.seu.vcampus.common.user.UserRole role) { return false; }
        };
        StudentService service = new StudentServiceImpl(database.transactions(),
                new StripedResourceLockManager(), repository, new StudentChangeRepository(),
                organizations, users, "college-admin");

        var page = service.searchStudents(new StudentSearchQuery(null,
                "department-2", null, null, null, 1, 20), "department-1");

        assertThat(page.items()).extracting(item -> item.studentId())
                .containsExactly("student-1");
    }
}
