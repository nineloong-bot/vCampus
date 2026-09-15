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
abstract class StudentAdmissionCoordinatorHelpers2 extends StudentAdmissionCoordinatorHelpers3 {
    protected StudentAdmissionCoordinatorHelpers2(TransactionManager transactions, ResourceLockManager locks,
            RequestDeduplicator deduplicator, OrganizationRepository organizations,
            CampusCardNumberGenerator campusCards, StudentNumberGenerator studentNumbers,
            UserAccountProvisioningPort accounts, StudentRepository students,
            StudentChangeRepository changes) {
        super(transactions, locks, deduplicator, organizations, campusCards, studentNumbers, accounts, students, changes);
    }


    protected StudentAdmissionResult createManualInTransaction(TransactionContext tx,
            CreateStudentManualCommand command, RequestContext request,
            String trustedDepartmentId) throws Exception {
        var replay = deduplicator.replayCompleted(tx, request.requestId());
        if (replay.isPresent()) return replayResult(replay.get());
        ValidatedManual validated = validateManualOrganization(tx.connection(), command);
        requireDepartment(validated.major(), trustedDepartmentId);
        if (students.existsByStudentNumber(tx.connection(), command.studentNumber())) {
            throw new StudentAdmissionException("STUDENT_NUMBER_DUPLICATE", "学号已被使用");
        }
        if (students.existsByIdDocumentNumber(tx.connection(), command.idDocumentNumber())) {
            throw new StudentAdmissionException("STUDENT_ID_DOCUMENT_DUPLICATE", "身份证件号已被使用");
        }
        var account = accounts.createStudentAccount(tx, command.campusCardNumber(),
                "12345678".toCharArray());
        failureInjector.reached(AdmissionFailurePoint.AFTER_ACCOUNT);
        Instant now = Instant.now();
        Student student = new Student(UUID.randomUUID().toString(), account.userId(),
                command.studentNumber(), command.studentType(), command.studentName(), command.gender(),
                null, null, validated.major().majorId(), command.classId(), command.enrollmentDate(),
                StudentStatus.ACTIVE, 0, now, now);
        students.insertManual(tx.connection(), student, command.idDocumentType(),
                command.idDocumentNumber(), command.birthDate());
        failureInjector.reached(AdmissionFailurePoint.AFTER_PROFILE);
        changes.insertChange(tx.connection(), UUID.randomUUID().toString(), student.studentId(),
                "MANUAL_CREATE", null,
                "studentNumber=" + student.studentNumber() + ";classId=" + student.classId(),
                "管理员手动新增学生", request.userId(), command.enrollmentDate(), now);
        failureInjector.reached(AdmissionFailurePoint.AFTER_AUDIT);
        StudentView view = view(student, command.campusCardNumber(), validated.major(),
                validated.studentClass(), validated.departmentName());
        StudentAdmissionResult result = new StudentAdmissionResult(view,
                command.campusCardNumber(), command.studentNumber(), true);
        Message requestMessage = new Message(request.requestId(), MessageType.REQUEST,
                MANUAL_COMMAND, null, command, System.currentTimeMillis());
        deduplicator.storeCompleted(tx, requestMessage, ResponseBody.success(result));
        failureInjector.reached(AdmissionFailurePoint.AFTER_DEDUP);
        return result;
    }

    protected StudentAdmissionResult admitInTransaction(TransactionContext tx,
            CreateStudentAdmissionCommand command, RequestContext request,
            String trustedDepartmentId) throws Exception {
        var replay = deduplicator.replayCompleted(tx, request.requestId());
        if (replay.isPresent()) return replayResult(replay.get());
        Message requestMessage = new Message(request.requestId(), MessageType.REQUEST, COMMAND,
                null, command, System.currentTimeMillis());
        ValidatedAdmission validated = validate(tx.connection(), command, trustedDepartmentId);
        String campusCard = campusCards.next(tx, command.studentType(), command.enrollmentYear());
        String studentNumber = studentNumbers.next(tx, validated.major().majorCode(),
                command.enrollmentYear(), validated.studentClass().classNumber());
        failureInjector.reached(AdmissionFailurePoint.AFTER_NUMBERS);
        return locks.withLocks(List.of(new ResourceKey("LOGIN_ID", campusCard)), () -> {
            try {
                return persist(tx, command, request, validated, campusCard, studentNumber);
            } catch (RuntimeException error) {
                throw error;
            } catch (Exception error) {
                throw new StudentAdmissionException("STUDENT_NUMBER_GENERATION_FAILED", error.getMessage());
            }
        });
    }
}
