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
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

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
                new AccessOrganizationRepository(), userId -> "213240001", "teacher-1");

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
}
