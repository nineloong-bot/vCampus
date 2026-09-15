package edu.seu.vcampus.server.student.service;

import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.MessageType;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.BatchImportCommand;
import edu.seu.vcampus.common.student.BatchImportResult;
import edu.seu.vcampus.common.student.CreateStudentAdmissionCommand;
import edu.seu.vcampus.common.student.CreateStudentManualCommand;
import edu.seu.vcampus.common.student.StudentFieldError;
import edu.seu.vcampus.common.student.StudentFieldValidator;
import edu.seu.vcampus.common.student.StudentAdmissionResult;
import edu.seu.vcampus.common.student.StudentStatus;
import edu.seu.vcampus.common.student.StudentType;
import edu.seu.vcampus.common.student.StudentView;
import edu.seu.vcampus.server.concurrency.ResourceKey;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionContext;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.routing.RequestContext;
import edu.seu.vcampus.server.routing.RequestDeduplicator;
import edu.seu.vcampus.server.student.domain.Major;
import edu.seu.vcampus.server.student.domain.Student;
import edu.seu.vcampus.server.student.domain.StudentClass;
import edu.seu.vcampus.server.student.numbering.CampusCardNumberGenerator;
import edu.seu.vcampus.server.student.numbering.StudentNumberGenerator;
import edu.seu.vcampus.server.student.repository.OrganizationRepository;
import edu.seu.vcampus.server.student.repository.StudentChangeRepository;
import edu.seu.vcampus.server.student.repository.StudentRepository;
import edu.seu.vcampus.server.user.service.UserAccountProvisioningPort;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Provides focused helper operations for {@link StudentAdmissionCoordinator}. */
abstract class StudentAdmissionCoordinatorHelpers4 extends StudentAdmissionCoordinatorSupport {
    protected StudentAdmissionCoordinatorHelpers4(TransactionManager transactions, ResourceLockManager locks,
            RequestDeduplicator deduplicator, OrganizationRepository organizations,
            CampusCardNumberGenerator campusCards, StudentNumberGenerator studentNumbers,
            UserAccountProvisioningPort accounts, StudentRepository students,
            StudentChangeRepository changes) {
        super(transactions, locks, deduplicator, organizations, campusCards, studentNumbers, accounts, students, changes);
    }


    protected ValidatedManual validateManualOrganization(java.sql.Connection connection,
            CreateStudentManualCommand command) {
        StudentClass studentClass = organizations.findClass(connection, command.classId())
                .orElseThrow(() -> new StudentAdmissionException(
                        "STUDENT_ORGANIZATION_MISMATCH", "班级不存在"));
        Major major = organizations.findMajor(connection, studentClass.majorId())
                .orElseThrow(() -> new StudentAdmissionException(
                        "STUDENT_ORGANIZATION_MISMATCH", "班级所属专业不存在"));
        var department = organizations.findDepartment(connection, major.departmentId())
                .orElseThrow(() -> new StudentAdmissionException(
                        "STUDENT_ORGANIZATION_MISMATCH", "专业所属院系不存在"));
        if (!department.active() || !major.active() || !studentClass.active()) {
            throw new StudentAdmissionException("STUDENT_CLASS_INACTIVE", "所选班级或上级组织已停用");
        }
        if (studentClass.enrollmentYear() != command.enrollmentDate().getYear()) {
            throw new StudentAdmissionException("STUDENT_ORGANIZATION_MISMATCH", "入学日期年份必须与班级年级一致");
        }
        String expectedPrefix = major.majorCode()
                + String.format("%02d", studentClass.enrollmentYear() % 100)
                + studentClass.classNumber();
        if (!command.studentNumber().startsWith(expectedPrefix)) {
            throw new StudentAdmissionException("STUDENT_NUMBER_INVALID",
                    "学号必须与所选专业、年级和班级匹配，应以 " + expectedPrefix + " 开头");
        }
        return new ValidatedManual(major, studentClass, department.departmentName());
    }

    protected StudentView view(Student student, String campusCard, Major major,
            StudentClass studentClass, String departmentName) {
        return new StudentView(student.studentId(), student.userId(), campusCard,
                student.studentNumber(), student.studentType(), student.studentName(), student.gender(),
                student.email(), student.phone(), student.majorId(), student.classId(),
                student.enrollmentDate(), student.status(), student.rowVersion(), departmentName,
                major.majorName(), studentClass.className());
    }

    protected static StudentAdmissionResult replayResult(ResponseBody<?> body) {
        if (!body.success() || !(body.data() instanceof StudentAdmissionResult result)) {
            throw new StudentAdmissionException(body.code(), body.message());
        }
        return result;
    }

    protected static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }

    protected static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Provides validated admission behavior. */
    protected record ValidatedAdmission(Major major, StudentClass studentClass, String sequenceKey) { }
    /** Provides validated manual behavior. */
    protected record ValidatedManual(Major major, StudentClass studentClass,
                                   String departmentName) { }
}
