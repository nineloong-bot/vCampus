package edu.seu.vcampus.server.student.service;

import edu.seu.vcampus.common.student.*;
import edu.seu.vcampus.server.concurrency.ResourceKey;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.persistence.TransactionContext;
import edu.seu.vcampus.server.student.numbering.AccessStudentNumberGenerator;
import edu.seu.vcampus.server.student.repository.NumberSequenceRepository;
import edu.seu.vcampus.server.student.domain.Student;
import edu.seu.vcampus.server.student.repository.OrganizationRepository;
import edu.seu.vcampus.server.student.repository.StudentChangeRepository;
import edu.seu.vcampus.server.student.repository.StudentRepository;
import edu.seu.vcampus.server.user.service.UserQueryPort;

import java.time.Instant;
import java.sql.Connection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import edu.seu.vcampus.common.paging.PageResult;

/** Implements focused public operations for {@link StudentServiceImpl}. */
abstract class StudentServiceImplOperations4 extends StudentServiceImplHelpers1 {
    protected StudentServiceImplOperations4(TransactionManager transactions, ResourceLockManager locks,
            StudentRepository students, StudentChangeRepository changes,
            OrganizationRepository organizations, UserQueryPort users, String operatorUserId) {
        super(transactions, locks, students, changes, organizations, users, operatorUserId);
    }


    @Override public StudentView updateEnrollment(UpdateStudentEnrollmentCommand command,
            String auditUserId, String trustedDepartmentId) {
        Objects.requireNonNull(command.effectiveDate()); requireReason(command.reason());
        var target = transactions.inTransaction(connection -> organizations.findClass(connection, command.classId())
                .filter(value -> value.active()).orElseThrow(() -> new StudentAdmissionException(
                        "STUDENT_CLASS_INACTIVE", "Target class is unavailable")));
        var major = transactions.inTransaction(connection -> organizations.findMajor(connection, target.majorId())
                .filter(value -> value.active()).orElseThrow(() -> new StudentAdmissionException(
                        "STUDENT_CLASS_INACTIVE", "Target major is unavailable")));
        String sequenceKey = "STUDENT_NUMBER:" + major.majorCode() + ":"
                + String.format("%02d", target.enrollmentYear() % 100) + ":" + target.classNumber();
        return locks.withLocks(List.of(new ResourceKey("NUMBER_SEQUENCE", sequenceKey),
                new ResourceKey("STUDENT", command.studentId())), () -> transactions.inTransaction(connection -> {
            Student before = requireById(connection, command.studentId(), trustedDepartmentId);
            requireSameMajor(before, target.majorId());
            String nextNumber = new AccessStudentNumberGenerator(new NumberSequenceRepository()).next(
                    new TransactionContext(connection, auditUserId, "student-service"), major.majorCode(),
                    target.enrollmentYear(), target.classNumber());
            students.updateEnrollment(connection, command.studentId(), target.classId(), nextNumber,
                    command.expectedVersion(), Instant.now());
            changes.insertChange(connection, UUID.randomUUID().toString(), command.studentId(),
                    "CLASS_CHANGE", before.classId() + ":" + before.studentNumber(),
                    target.classId() + ":" + nextNumber, command.reason(), auditUserId,
                    command.effectiveDate(), Instant.now());
            return view(connection, requireById(connection, command.studentId()));
        }));
    }

    @Override public StudentEligibility getEnrollmentEligibility(String userId) {
        return transactions.inTransaction(connection -> {
            Student student = students.findByUserId(connection, userId)
                    .orElseThrow(StudentNotFoundException::new);
            return eligibility(connection, student);
        });
    }

    @Override public StudentEligibility getEnrollmentEligibilityByStudentNumber(String studentNumber) {
        return transactions.inTransaction(connection -> students.findByStudentNumber(connection, studentNumber)
                .map(student -> eligibility(connection, student)).orElse(null));
    }

    @Override public StudentIdentity findByUserId(String userId) {
        Student student = transactions.inTransaction(connection -> students.findByUserId(connection, userId)
                .orElseThrow(StudentNotFoundException::new));
        return new StudentIdentity(student.studentId(), student.userId(),
                loginId(student.userId()), student.studentNumber(),
                student.studentType(), student.majorId(), student.classId(), student.status());
    }

    @Override public boolean existsActiveStudent(String studentId) {
        return transactions.inTransaction(connection -> students.findById(connection, studentId)
                .map(student -> student.status() == StudentStatus.ACTIVE).orElse(false));
    }

    @Override public List<StudentChangeView> listChanges(String studentId) {
        return listChanges(studentId, null);
    }

    @Override public List<StudentChangeView> listChanges(String studentId,
            String trustedDepartmentId) {
        return transactions.inTransaction(connection -> {
            requireById(connection, studentId, trustedDepartmentId);
            return changes.listByStudentId(connection, studentId);
        });
    }
}
