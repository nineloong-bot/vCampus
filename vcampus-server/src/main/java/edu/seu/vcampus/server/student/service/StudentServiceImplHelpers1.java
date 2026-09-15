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

/** Provides focused helper operations for {@link StudentServiceImpl}. */
abstract class StudentServiceImplHelpers1 extends StudentServiceImplHelpers2 {
    protected StudentServiceImplHelpers1(TransactionManager transactions, ResourceLockManager locks,
            StudentRepository students, StudentChangeRepository changes,
            OrganizationRepository organizations, UserQueryPort users, String operatorUserId) {
        super(transactions, locks, students, changes, organizations, users, operatorUserId);
    }


    protected static void requireSameMajor(Student before, String targetMajorId) {
        if (!before.majorId().equals(targetMajorId))
            throw new StudentAdmissionException("STUDENT_TRANSFER_REQUIRED", "跨专业变更请通过转专业审核和生效办理");
    }

    protected StudentEligibility eligibility(Connection connection, Student student) {
        var major = organizations.findMajor(connection, student.majorId())
                .orElseThrow(() -> new StudentAdmissionException(
                        "STUDENT_MAJOR_NOT_FOUND", "学生专业不存在"));
        var studentClass = organizations.findClass(connection, student.classId())
                .orElseThrow(() -> new StudentAdmissionException(
                        "STUDENT_CLASS_NOT_FOUND", "学生班级不存在"));
        boolean eligible = student.status() == StudentStatus.ACTIVE;
        return new StudentEligibility(student.studentId(), student.status(), eligible,
                eligible ? "ELIGIBLE" : "STATUS_" + student.status(),
                major.majorCode(), studentClass.enrollmentYear());
    }

    protected Student requireById(java.sql.Connection connection, String studentId) {
        return students.findById(connection, studentId).orElseThrow(StudentNotFoundException::new);
    }

    protected Student requireById(java.sql.Connection connection, String studentId,
            String trustedDepartmentId) {
        Student student = requireById(connection, studentId);
        if (trustedDepartmentId != null
                && !students.belongsToDepartment(connection, studentId, trustedDepartmentId)) {
            throw new IllegalArgumentException("COMMON_FORBIDDEN");
        }
        return student;
    }

    protected StudentView view(java.sql.Connection connection, Student student) {
        var major = organizations.findMajor(connection, student.majorId());
        String majorName = major.map(m -> m.majorName()).orElse(null);
        String departmentName = major.flatMap(m -> organizations.findDepartment(connection, m.departmentId()))
                .map(d -> d.departmentName()).orElse(null);
        String className = organizations.findClass(connection, student.classId())
                .map(c -> c.className()).orElse(null);
        return new StudentView(student.studentId(), student.userId(), loginId(student.userId()),
                student.studentNumber(), student.studentType(), student.studentName(), student.gender(),
                student.email(), student.phone(), student.majorId(), student.classId(), student.enrollmentDate(),
                student.status(), student.rowVersion(), departmentName, majorName, className);
    }

    protected String loginId(String userId) {
        return users.findByUserId(userId).orElseThrow(() ->
                new IllegalStateException("STUDENT_USER_ACCOUNT_NOT_FOUND")).loginId();
    }

    protected <T> T withStudent(String studentId, java.util.function.Supplier<T> action) {
        return locks.withLocks(List.of(new ResourceKey("STUDENT", studentId)), action);
    }

    protected static String normalizeEmail(String email) {
        String value = blankToNull(email);
        if (value != null && !value.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))
            throw new IllegalArgumentException("Invalid email");
        return value;
    }
    protected static String normalizeStudentNumber(String studentNumber) {
        String value = blankToNull(studentNumber);
        if (value == null || !value.matches("\\d{8}"))
            throw new StudentAdmissionException("STUDENT_NUMBER_INVALID", "Student number must be 8 digits");
        return value;
    }
    protected static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    protected static void requireReason(String reason) { if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reason is required"); }
}
