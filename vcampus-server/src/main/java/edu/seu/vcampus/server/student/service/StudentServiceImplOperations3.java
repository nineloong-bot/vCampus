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
abstract class StudentServiceImplOperations3 extends StudentServiceImplOperations4 {
    protected StudentServiceImplOperations3(TransactionManager transactions, ResourceLockManager locks,
            StudentRepository students, StudentChangeRepository changes,
            OrganizationRepository organizations, UserQueryPort users, String operatorUserId) {
        super(transactions, locks, students, changes, organizations, users, operatorUserId);
    }


    @Override public StudentView updateStudentAcademic(UpdateStudentAcademicCommand command,
            String auditUserId) {
        return updateStudentAcademic(command, auditUserId, null);
    }

    @Override public StudentView updateStudentAcademic(UpdateStudentAcademicCommand command,
            String auditUserId, String trustedDepartmentId) {
        String studentNumber = normalizeStudentNumber(command.studentNumber());
        Objects.requireNonNull(command.classId());
        Objects.requireNonNull(command.status());
        Objects.requireNonNull(command.studentType());
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
                    || !before.studentNumber().equals(studentNumber)
                    || before.studentType() != command.studentType();
            boolean statusChanged = before.status() != command.status();
            if (statusChanged && !validTransition(before.status(), command.status()))
                throw new StudentAdmissionException("STUDENT_STATUS_TRANSITION_INVALID",
                        "Invalid student status transition");
            Instant now = Instant.now();
            students.updateAcademicProfile(connection, command.studentId(), target.classId(),
                    studentNumber, command.studentType().name(), command.status().name(),
                    command.enrolled(), command.onCampus(), blankToNull(command.campus()),
                    blankToNull(command.educationLevel()), blankToNull(command.trainingMode()),
                    command.programLengthYears(),
                    command.attendanceMode() == null ? null : command.attendanceMode().name(),
                    blankToNull(command.degreeName()), blankToNull(command.educationName()),
                    command.expectedGraduationDate(), command.graduationDate(),
                    blankToNull(command.studentSource()), blankToNull(command.graduateStudyMode()),
                    blankToNull(command.counselorName()), blankToNull(command.counselorContact()),
                    command.expectedVersion(), now);
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
            if (!enrollmentChanged && !statusChanged) {
                changes.insertChange(connection, UUID.randomUUID().toString(), command.studentId(),
                        "ACADEMIC_CHANGE", "", "学籍字段修改", command.reason(), auditUserId,
                        command.effectiveDate(), now);
            }
            return view(connection, requireById(connection, command.studentId()));
        }));
    }

    @Override public StudentView updateEnrollment(UpdateStudentEnrollmentCommand command) {
        return updateEnrollment(command, operatorUserId);
    }

    @Override public StudentView updateEnrollment(UpdateStudentEnrollmentCommand command,
            String auditUserId) {
        return updateEnrollment(command, auditUserId, null);
    }
}
