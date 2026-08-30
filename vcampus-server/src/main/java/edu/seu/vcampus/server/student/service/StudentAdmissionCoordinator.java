package edu.seu.vcampus.server.student.service;

import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.MessageType;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.CreateStudentAdmissionCommand;
import edu.seu.vcampus.common.student.StudentAdmissionResult;
import edu.seu.vcampus.common.student.StudentStatus;
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
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Coordinates an admission as one lock-ordered, idempotent Access transaction. */
public final class StudentAdmissionCoordinator implements StudentAdmissionService {
    private static final String COMMAND = "STUDENT_CREATE";
    private final TransactionManager transactions;
    private final ResourceLockManager locks;
    private final RequestDeduplicator deduplicator;
    private final OrganizationRepository organizations;
    private final CampusCardNumberGenerator campusCards;
    private final StudentNumberGenerator studentNumbers;
    private final UserAccountProvisioningPort accounts;
    private final StudentRepository students;
    private final StudentChangeRepository changes;
    private AdmissionFailureInjector failureInjector = AdmissionFailureInjector.NONE;

    public StudentAdmissionCoordinator(TransactionManager transactions, ResourceLockManager locks,
            RequestDeduplicator deduplicator, OrganizationRepository organizations,
            CampusCardNumberGenerator campusCards, StudentNumberGenerator studentNumbers,
            UserAccountProvisioningPort accounts, StudentRepository students,
            StudentChangeRepository changes) {
        this.transactions = Objects.requireNonNull(transactions);
        this.locks = Objects.requireNonNull(locks);
        this.deduplicator = Objects.requireNonNull(deduplicator);
        this.organizations = Objects.requireNonNull(organizations);
        this.campusCards = Objects.requireNonNull(campusCards);
        this.studentNumbers = Objects.requireNonNull(studentNumbers);
        this.accounts = Objects.requireNonNull(accounts);
        this.students = Objects.requireNonNull(students);
        this.changes = Objects.requireNonNull(changes);
    }

    public void setFailureInjector(AdmissionFailureInjector failureInjector) {
        this.failureInjector = Objects.requireNonNull(failureInjector);
    }

    @Override
    public StudentAdmissionResult admit(CreateStudentAdmissionCommand command, RequestContext request) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(request, "request");
        var replay = deduplicator.replayCompleted(request.requestId());
        if (replay.isPresent()) return replayResult(replay.get());
        ValidatedAdmission initial = transactions.inTransaction(connection ->
                validate(connection, command));
        List<ResourceKey> sequenceLocks = List.of(
                new ResourceKey("NUMBER_SEQUENCE", "CAMPUS_CARD_GLOBAL"),
                new ResourceKey("NUMBER_SEQUENCE", initial.sequenceKey()));
        return locks.withLocks(sequenceLocks, () -> {
            var lockedReplay = deduplicator.replayCompleted(request.requestId());
            if (lockedReplay.isPresent()) return replayResult(lockedReplay.get());
            return transactions.inTransaction(connection -> admitInTransaction(
                    new TransactionContext(connection, request.userId(), request.clientInstanceId()),
                    command, request));
        });
    }

    private StudentAdmissionResult admitInTransaction(TransactionContext tx,
            CreateStudentAdmissionCommand command, RequestContext request) throws Exception {
        var replay = deduplicator.replayCompleted(tx, request.requestId());
        if (replay.isPresent()) return replayResult(replay.get());
        Message requestMessage = new Message(request.requestId(), MessageType.REQUEST, COMMAND,
                null, command, System.currentTimeMillis());
        ValidatedAdmission validated = validate(tx.connection(), command);
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

    private StudentAdmissionResult persist(TransactionContext tx,
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
        StudentView view = new StudentView(student.studentId(), student.userId(), campusCard,
                student.studentNumber(), student.studentType(), student.studentName(), student.gender(),
                student.email(), student.phone(), student.majorId(), student.classId(),
                student.enrollmentDate(), student.status(), student.rowVersion());
        StudentAdmissionResult result = new StudentAdmissionResult(view, campusCard, studentNumber,
                true);
        Message requestMessage = new Message(request.requestId(), MessageType.REQUEST, COMMAND, null,
                command, System.currentTimeMillis());
        deduplicator.storeCompleted(tx, requestMessage, ResponseBody.success(result));
        failureInjector.reached(AdmissionFailurePoint.AFTER_DEDUP);
        return result;
    }

    private ValidatedAdmission validate(java.sql.Connection connection,
            CreateStudentAdmissionCommand command) {
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

    private static StudentAdmissionResult replayResult(ResponseBody<?> body) {
        if (!body.success() || !(body.data() instanceof StudentAdmissionResult result)) {
            throw new StudentAdmissionException(body.code(), body.message());
        }
        return result;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record ValidatedAdmission(Major major, StudentClass studentClass, String sequenceKey) { }
}
