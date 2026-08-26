package edu.seu.vcampus.server.student.numbering;

import edu.seu.vcampus.common.student.StudentType;
import edu.seu.vcampus.server.concurrency.ResourceKey;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionContext;
import edu.seu.vcampus.server.student.repository.NumberSequenceRepository;
import edu.seu.vcampus.server.student.domain.Department;
import edu.seu.vcampus.server.student.domain.Major;
import edu.seu.vcampus.server.student.domain.StudentClass;
import edu.seu.vcampus.server.student.repository.AccessOrganizationRepository;
import edu.seu.vcampus.server.student.support.StudentAccessTestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NumberSequenceConcurrencyTest {
    private StudentAccessTestDatabase database;
    private AccessCampusCardNumberGenerator campusCards;
    private AccessStudentNumberGenerator studentNumbers;
    private StripedResourceLockManager locks;

    @BeforeEach
    void setUp() throws Exception {
        database = new StudentAccessTestDatabase();
        try (var connection = database.provider().open()) {
            var organizations = new AccessOrganizationRepository();
            organizations.insertDepartment(connection,
                    new Department("dep-eng", "ENG", "工学院", true, 0));
            organizations.insertMajor(connection,
                    new Major("major-honors", "dep-eng", "09J", "计算机拔尖班", true, 0));
            organizations.insertClass(connection,
                    new StudentClass("class-1", "major-honors", "09J-2024-1", "拔尖一班",
                            2024, 1, true, 0));
        }
        var sequences = new NumberSequenceRepository();
        campusCards = new AccessCampusCardNumberGenerator(sequences);
        studentNumbers = new AccessStudentNumberGenerator(sequences);
        locks = new StripedResourceLockManager();
    }

    @Test
    void twentyConcurrentAdmissionsReceiveUniqueContiguousNumbers() throws Exception {
        var executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<AllocatedNumbers>> calls = new ArrayList<>();
            for (int index = 0; index < 20; index++) {
                calls.add(() -> locks.withLocks(List.of(
                        new ResourceKey("NUMBER_SEQUENCE", "CAMPUS_CARD_GLOBAL"),
                        new ResourceKey("NUMBER_SEQUENCE", "STUDENT_NUMBER:09J:24:1")),
                        () -> database.transactions().inTransaction(connection -> {
                            var tx = new TransactionContext(connection);
                            return new AllocatedNumbers(
                                    campusCards.next(tx, StudentType.UNDERGRADUATE, 2024),
                                    studentNumbers.next(tx, "09J", 2024, 1));
                        })));
            }
            List<AllocatedNumbers> results = executor.invokeAll(calls).stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception error) {
                            throw new AssertionError(error);
                        }
                    }).toList();

            List<String> campusValues = results.stream().map(AllocatedNumbers::campusCard).toList();
            List<String> studentValues = results.stream().map(AllocatedNumbers::studentNumber).toList();
            int campusSequence = database.sequenceValue("CAMPUS_CARD_GLOBAL");
            int studentSequence = database.sequenceValue("STUDENT_NUMBER:09J:24:1");
            assertThat(campusValues)
                    .as("campus=%s, student=%s, database=%s/%s",
                            campusValues, studentValues, campusSequence, studentSequence)
                    .doesNotHaveDuplicates();
            assertThat(studentValues)
                    .as("campus=%s, student=%s, database=%s/%s",
                            campusValues, studentValues, campusSequence, studentSequence)
                    .doesNotHaveDuplicates();
            assertThat(campusSequence).isEqualTo(20);
            assertThat(studentSequence).isEqualTo(20);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rollbackDoesNotConsumeNumber() throws Exception {
        assertThatThrownBy(() -> database.transactions().inTransaction(connection -> {
            campusCards.next(new TransactionContext(connection), StudentType.DOCTORATE, 2025);
            throw new IllegalStateException("injected");
        })).isInstanceOf(IllegalStateException.class).hasMessage("injected");

        assertThat(database.sequenceValue("CAMPUS_CARD_GLOBAL")).isZero();
    }

    private record AllocatedNumbers(String campusCard, String studentNumber) {
    }
}
