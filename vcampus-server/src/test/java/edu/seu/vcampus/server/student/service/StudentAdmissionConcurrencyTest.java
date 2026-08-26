package edu.seu.vcampus.server.student.service;

import edu.seu.vcampus.common.student.CreateStudentAdmissionCommand;
import edu.seu.vcampus.common.student.StudentAdmissionResult;
import edu.seu.vcampus.common.student.StudentType;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.routing.RequestContext;
import edu.seu.vcampus.server.routing.RequestDeduplicator;
import edu.seu.vcampus.server.student.domain.Department;
import edu.seu.vcampus.server.student.domain.Major;
import edu.seu.vcampus.server.student.domain.StudentClass;
import edu.seu.vcampus.server.student.numbering.AccessCampusCardNumberGenerator;
import edu.seu.vcampus.server.student.numbering.AccessStudentNumberGenerator;
import edu.seu.vcampus.server.student.repository.AccessOrganizationRepository;
import edu.seu.vcampus.server.student.repository.NumberSequenceRepository;
import edu.seu.vcampus.server.student.repository.StudentChangeRepository;
import edu.seu.vcampus.server.student.repository.StudentRepository;
import edu.seu.vcampus.server.student.support.StudentAccessTestDatabase;
import edu.seu.vcampus.server.user.service.ProvisionedUserAccount;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class StudentAdmissionConcurrencyTest {
    @Test
    void twentyAdmissionsAreUniqueGaplessAndOneToOne() throws Exception {
        var database = new StudentAccessTestDatabase();
        var organizations = new AccessOrganizationRepository();
        database.transactions().inTransaction(connection -> {
            organizations.insertDepartment(connection,
                    new Department("department-1", "CS", "计算机学院", true, 0));
            organizations.insertMajor(connection,
                    new Major("major-1", "department-1", "09J", "CS拔尖班", true, 0));
            organizations.insertClass(connection,
                    new StudentClass("class-1", "major-1", "09J-24-1", "CS拔尖24-1", 2024, 1, true, 0));
            return null;
        });
        var sequences = new NumberSequenceRepository();
        var coordinator = new StudentAdmissionCoordinator(database.transactions(),
                new StripedResourceLockManager(), new RequestDeduplicator(database.transactions()),
                organizations, new AccessCampusCardNumberGenerator(sequences),
                new AccessStudentNumberGenerator(sequences), (transaction, loginId, password) -> {
                    String userId = UUID.randomUUID().toString();
                    try (var statement = transaction.connection().prepareStatement(
                            "INSERT INTO tblUser (userId, loginId) VALUES (?, ?)")) {
                        statement.setString(1, userId);
                        statement.setString(2, loginId);
                        statement.executeUpdate();
                    }
                    return new ProvisionedUserAccount(userId, loginId, true);
                }, new StudentRepository(), new StudentChangeRepository());
        var start = new CountDownLatch(1);
        var tasks = new ArrayList<Callable<StudentAdmissionResult>>();
        for (int index = 1; index <= 20; index++) {
            int number = index;
            tasks.add(() -> {
                start.await();
                var command = new CreateStudentAdmissionCommand("学生" + number, "MALE", null,
                        null, "major-1", "class-1", 2024, StudentType.UNDERGRADUATE);
                return coordinator.admit(command,
                        new RequestContext(UUID.randomUUID().toString(), "admin-1", "client-" + number));
            });
        }
        try (var executor = Executors.newFixedThreadPool(20)) {
            var futures = tasks.stream().map(executor::submit).toList();
            start.countDown();
            var results = new ArrayList<StudentAdmissionResult>();
            for (var future : futures) results.add(future.get());
            assertThat(new HashSet<>(results.stream().map(StudentAdmissionResult::campusCardNumber).toList()))
                    .hasSize(20);
            assertThat(new HashSet<>(results.stream().map(StudentAdmissionResult::studentNumber).toList()))
                    .hasSize(20);
        }
        assertThat(database.sequenceValue("CAMPUS_CARD_GLOBAL")).isEqualTo(20);
        assertThat(database.sequenceValue("STUDENT_NUMBER:09J:24:1")).isEqualTo(20);
        assertThat(database.count("tblUser")).isEqualTo(20);
        assertThat(database.count("tblStudent")).isEqualTo(20);
        assertThat(database.count("tblStudentChange")).isEqualTo(20);
        assertThat(database.count("tblRequestDedup")).isEqualTo(20);
    }
}
