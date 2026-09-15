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
abstract class StudentAdmissionCoordinatorHelpers3 extends StudentAdmissionCoordinatorHelpers4 {
    protected StudentAdmissionCoordinatorHelpers3(TransactionManager transactions, ResourceLockManager locks,
            RequestDeduplicator deduplicator, OrganizationRepository organizations,
            CampusCardNumberGenerator campusCards, StudentNumberGenerator studentNumbers,
            UserAccountProvisioningPort accounts, StudentRepository students,
            StudentChangeRepository changes) {
        super(transactions, locks, deduplicator, organizations, campusCards, studentNumbers, accounts, students, changes);
    }


    protected StudentAdmissionResult persist(TransactionContext tx,
            CreateStudentAdmissionCommand command, RequestContext request,
            ValidatedAdmission validated, String campusCard, String studentNumber) throws Exception {
        var account = accounts.createStudentAccount(tx, campusCard, "12345678".toCharArray());
        failureInjector.reached(AdmissionFailurePoint.AFTER_ACCOUNT);
        Instant now = Instant.now();
        LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
        Student student = new Student(UUID.randomUUID().toString(), account.userId(), studentNumber,
                command.studentType(), requireText(command.studentName(), "studentName"),
                requireText(command.gender(), "gender"), blankToNull(command.email()),
                blankToNull(command.phone()), command.majorId(), command.classId(), today,
                StudentStatus.ACTIVE, 0, now, now);
        students.insert(tx.connection(), student);
        failureInjector.reached(AdmissionFailurePoint.AFTER_PROFILE);
        changes.insertAdmission(tx.connection(), UUID.randomUUID().toString(), student.studentId(),
                "studentNumber=" + studentNumber + ";classId=" + student.classId(),
                request.userId(), today, now);
        failureInjector.reached(AdmissionFailurePoint.AFTER_AUDIT);
        String majorName = organizations.findMajor(tx.connection(), student.majorId())
                .map(m -> m.majorName()).orElse(null);
        String departmentName = majorName != null ? organizations.findMajor(tx.connection(), student.majorId())
                .flatMap(m -> organizations.findDepartment(tx.connection(), m.departmentId()))
                .map(d -> d.departmentName()).orElse(null) : null;
        String className = organizations.findClass(tx.connection(), student.classId())
                .map(c -> c.className()).orElse(null);
        StudentView view = new StudentView(student.studentId(), student.userId(), campusCard,
                student.studentNumber(), student.studentType(), student.studentName(), student.gender(),
                student.email(), student.phone(), student.majorId(), student.classId(),
                student.enrollmentDate(), student.status(), student.rowVersion(),
                departmentName, majorName, className);
        StudentAdmissionResult result = new StudentAdmissionResult(view, campusCard, studentNumber,
                true);
        Message requestMessage = new Message(request.requestId(), MessageType.REQUEST, COMMAND, null,
                command, System.currentTimeMillis());
        deduplicator.storeCompleted(tx, requestMessage, ResponseBody.success(result));
        failureInjector.reached(AdmissionFailurePoint.AFTER_DEDUP);
        return result;
    }

    protected ValidatedAdmission validate(java.sql.Connection connection,
            CreateStudentAdmissionCommand command, String trustedDepartmentId) {
        Objects.requireNonNull(command.studentType(), "studentType");
        Major major = organizations.findMajor(connection, command.majorId()).orElseThrow(() ->
                new StudentAdmissionException("STUDENT_ORGANIZATION_MISMATCH", "Major not found"));
        StudentClass studentClass = organizations.findClass(connection, command.classId()).orElseThrow(() ->
                new StudentAdmissionException("STUDENT_ORGANIZATION_MISMATCH", "Class not found"));
        var department = organizations.findDepartment(connection, major.departmentId()).orElseThrow(() ->
                new StudentAdmissionException("STUDENT_ORGANIZATION_MISMATCH", "Department not found"));
        if (!department.active() || !major.active() || !studentClass.active()) {
            throw new StudentAdmissionException("STUDENT_CLASS_INACTIVE", "Organization is inactive");
        }
        requireDepartment(major, trustedDepartmentId);
        if (!studentClass.majorId().equals(major.majorId())
                || studentClass.enrollmentYear() != command.enrollmentYear()) {
            throw new StudentAdmissionException("STUDENT_ORGANIZATION_MISMATCH",
                    "Class, major and enrollment year must match");
        }
        String key = "STUDENT_NUMBER:" + major.majorCode() + ":"
                + String.format("%02d", command.enrollmentYear() % 100) + ":"
                + studentClass.classNumber();
        return new ValidatedAdmission(major, studentClass, key);
    }

    protected static void requireDepartment(Major major, String trustedDepartmentId) {
        if (trustedDepartmentId != null && !trustedDepartmentId.equals(major.departmentId()))
            throw new IllegalArgumentException("COMMON_FORBIDDEN");
    }
}
