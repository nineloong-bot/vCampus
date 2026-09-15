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
abstract class StudentServiceImplOperations2 extends StudentServiceImplOperations3 {
    protected StudentServiceImplOperations2(TransactionManager transactions, ResourceLockManager locks,
            StudentRepository students, StudentChangeRepository changes,
            OrganizationRepository organizations, UserQueryPort users, String operatorUserId) {
        super(transactions, locks, students, changes, organizations, users, operatorUserId);
    }


    @Override public StudentView changeStatus(ChangeStudentStatusCommand command, String auditUserId,
            String trustedDepartmentId) {
        Objects.requireNonNull(command.status()); Objects.requireNonNull(command.effectiveDate());
        requireReason(command.reason());
        return withStudent(command.studentId(), () -> transactions.inTransaction(connection -> {
            Student before = requireById(connection, command.studentId(), trustedDepartmentId);
            if (!validTransition(before.status(), command.status())) {
                throw new StudentAdmissionException("STUDENT_STATUS_TRANSITION_INVALID",
                        "Invalid student status transition");
            }
            students.updateStatus(connection, command.studentId(), command.status().name(),
                    command.expectedVersion(), Instant.now());
            changes.insertChange(connection, UUID.randomUUID().toString(), command.studentId(),
                    "STATUS_CHANGE", before.status().name(), command.status().name(), command.reason(),
                    auditUserId, command.effectiveDate(), Instant.now());
            return view(connection, requireById(connection, command.studentId()));
        }));
    }

    @Override public StudentView updateStudentInfo(UpdateStudentInfoCommand command) {
        return updateStudentInfo(command, operatorUserId);
    }

    @Override public StudentView updateStudentInfo(UpdateStudentInfoCommand command, String auditUserId) {
        return updateStudentInfo(command, auditUserId, null);
    }

    @Override public StudentView updateStudentInfo(UpdateStudentInfoCommand command, String auditUserId,
            String trustedDepartmentId) {
        String studentNumber = normalizeStudentNumber(command.studentNumber());
        Objects.requireNonNull(command.classId());
        Objects.requireNonNull(command.status());
        Objects.requireNonNull(command.effectiveDate());
        requireReason(command.reason());
        return withStudent(command.studentId(), () -> transactions.inTransaction(connection -> {
            Student before = requireById(connection, command.studentId(), trustedDepartmentId);
            var target = organizations.findClass(connection, command.classId())
                    .filter(value -> value.active() || before.classId().equals(value.classId()))
                    .orElseThrow(() -> new StudentAdmissionException(
                            "STUDENT_CLASS_INACTIVE", "Target class is unavailable"));
            requireSameMajor(before, target.majorId());
            organizations.findMajor(connection, target.majorId())
                    .filter(value -> value.active() || before.classId().equals(target.classId()))
                    .orElseThrow(() -> new StudentAdmissionException(
                            "STUDENT_CLASS_INACTIVE", "Target major is unavailable"));
            boolean enrollmentChanged = !before.classId().equals(target.classId())
                    || !before.studentNumber().equals(studentNumber);
            boolean statusChanged = before.status() != command.status();
            if (!enrollmentChanged && !statusChanged)
                throw new StudentAdmissionException("STUDENT_INFO_UNCHANGED", "No academic changes");
            if (statusChanged && !validTransition(before.status(), command.status()))
                throw new StudentAdmissionException("STUDENT_STATUS_TRANSITION_INVALID",
                        "Invalid student status transition");
            Instant now = Instant.now();
            students.updateAcademicInfo(connection, command.studentId(), target.classId(),
                    studentNumber, command.status().name(), command.expectedVersion(), now);
            if (enrollmentChanged) {
                changes.insertChange(connection, UUID.randomUUID().toString(), command.studentId(),
                        "ENROLLMENT_CHANGE", before.classId() + ":" + before.studentNumber(),
                        target.classId() + ":" + studentNumber, command.reason(), auditUserId,
                        command.effectiveDate(), now);
            }
            if (statusChanged) {
                changes.insertChange(connection, UUID.randomUUID().toString(), command.studentId(),
                        "STATUS_CHANGE", before.status().name(), command.status().name(),
                        command.reason(), auditUserId, command.effectiveDate(), now);
            }
            return view(connection, requireById(connection, command.studentId()));
        }));
    }

    @Override public StudentView updateStudentAcademic(UpdateStudentAcademicCommand command) {
        return updateStudentAcademic(command, operatorUserId);
    }
}
