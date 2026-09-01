package edu.seu.vcampus.server.student.service;

import edu.seu.vcampus.common.student.StudentStatus;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.student.repository.AccessOrganizationRepository;
import edu.seu.vcampus.server.student.repository.StudentChangeRepository;
import edu.seu.vcampus.server.student.repository.StudentRepository;
import edu.seu.vcampus.server.student.support.StudentAccessTestDatabase;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;

class StudentQueryPortTest {
    @org.junit.jupiter.api.Test
    void identityPortExposesNoContactDetails() throws Exception {
        var database = new StudentAccessTestDatabase();
        var repository = new StudentRepository();
        database.transactions().inTransaction(connection -> {
            StudentFixtures.insertOrganization(connection, new AccessOrganizationRepository());
            repository.insert(connection, StudentProfileUpdateTest.student(StudentStatus.ACTIVE));
            return null;
        });
        StudentQueryPort port = new StudentServiceImpl(database.transactions(),
                new StripedResourceLockManager(), repository, new StudentChangeRepository(),
                new AccessOrganizationRepository(),
                StudentFixtures.userQueries("user-1", "213240001"), "admin-1");

        var identity = port.findByUserId("user-1");

        assertThat(identity.studentNumber()).isEqualTo("09024101");
        assertThat(Arrays.stream(identity.getClass().getRecordComponents()).map(RecordComponent::getName))
                .doesNotContain("email", "phone");
    }

    @ParameterizedTest
    @EnumSource(StudentStatus.class)
    void onlyActiveStatusIsEligible(StudentStatus status) throws Exception {
        var database = new StudentAccessTestDatabase();
        var repository = new StudentRepository();
        database.transactions().inTransaction(connection -> {
            StudentFixtures.insertOrganization(connection, new AccessOrganizationRepository());
            repository.insert(connection, StudentProfileUpdateTest.student(status));
            return null;
        });
        StudentQueryPort port = new StudentServiceImpl(database.transactions(),
                new StripedResourceLockManager(), repository, new StudentChangeRepository(),
                new AccessOrganizationRepository(),
                StudentFixtures.userQueries("user-1", "213240001"), "admin-1");

        var eligibility = port.getEnrollmentEligibility("user-1");

        assertThat(eligibility.eligible()).isEqualTo(status == StudentStatus.ACTIVE);
        assertThat(eligibility.reason()).isEqualTo(status == StudentStatus.ACTIVE
                ? "ELIGIBLE" : "STATUS_" + status);
    }
}
