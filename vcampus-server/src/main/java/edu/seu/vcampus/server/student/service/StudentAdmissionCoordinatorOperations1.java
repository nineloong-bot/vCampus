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

/** Implements focused public operations for {@link StudentAdmissionCoordinator}. */
abstract class StudentAdmissionCoordinatorOperations1 extends StudentAdmissionCoordinatorOperations2 {
    protected StudentAdmissionCoordinatorOperations1(TransactionManager transactions, ResourceLockManager locks,
            RequestDeduplicator deduplicator, OrganizationRepository organizations,
            CampusCardNumberGenerator campusCards, StudentNumberGenerator studentNumbers,
            UserAccountProvisioningPort accounts, StudentRepository students,
            StudentChangeRepository changes) {
        super(transactions, locks, deduplicator, organizations, campusCards, studentNumbers, accounts, students, changes);
    }


    /**
     * Performs the set failure injector operation.
     * @param failureInjector the failure injector
     */
    public void setFailureInjector(AdmissionFailureInjector failureInjector) {
        this.failureInjector = Objects.requireNonNull(failureInjector);
    }

    @Override
    public StudentAdmissionResult admit(CreateStudentAdmissionCommand command, RequestContext request) {
        return admit(command, request, null);
    }

    @Override
    public StudentAdmissionResult admit(CreateStudentAdmissionCommand command,
            RequestContext request, String trustedDepartmentId) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(request, "request");
        var replay = deduplicator.replayCompleted(request.requestId());
        if (replay.isPresent()) return replayResult(replay.get());
        ValidatedAdmission initial = transactions.inTransaction(connection ->
                validate(connection, command, trustedDepartmentId));
        List<ResourceKey> sequenceLocks = List.of(
                new ResourceKey("NUMBER_SEQUENCE", "CAMPUS_CARD_GLOBAL"),
                new ResourceKey("NUMBER_SEQUENCE", initial.sequenceKey()));
        return locks.withLocks(sequenceLocks, () -> {
            var lockedReplay = deduplicator.replayCompleted(request.requestId());
            if (lockedReplay.isPresent()) return replayResult(lockedReplay.get());
            return transactions.inTransaction(connection -> admitInTransaction(
                    new TransactionContext(connection, request.userId(), request.clientInstanceId()),
                    command, request, trustedDepartmentId));
        });
    }

    @Override
    public StudentAdmissionResult createManual(CreateStudentManualCommand raw, RequestContext request) {
        return createManual(raw, request, null);
    }

    @Override
    public StudentAdmissionResult createManual(CreateStudentManualCommand raw,
            RequestContext request, String trustedDepartmentId) {
        Objects.requireNonNull(raw, "command");
        Objects.requireNonNull(request, "request");
        CreateStudentManualCommand command = StudentFieldValidator.normalizeManual(raw);
        List<StudentFieldError> errors = StudentFieldValidator.validateManual(command,
                LocalDate.now(ZoneOffset.UTC));
        if (!errors.isEmpty()) throw new StudentAdmissionException(
                "STUDENT_MANUAL_FIELD_INVALID", errors.getFirst().message());
        var replay = deduplicator.replayCompleted(request.requestId());
        if (replay.isPresent()) return replayResult(replay.get());
        List<ResourceKey> manualLocks = List.of(
                new ResourceKey("LOGIN_ID", command.campusCardNumber()),
                new ResourceKey("STUDENT_NUMBER", command.studentNumber()),
                new ResourceKey("ID_DOCUMENT", command.idDocumentNumber()));
        return locks.withLocks(manualLocks, () -> {
            var lockedReplay = deduplicator.replayCompleted(request.requestId());
            if (lockedReplay.isPresent()) return replayResult(lockedReplay.get());
            return transactions.inTransaction(connection -> createManualInTransaction(
                    new TransactionContext(connection, request.userId(), request.clientInstanceId()),
                    command, request, trustedDepartmentId));
        });
    }

    @Override
    public BatchImportResult batchImport(BatchImportCommand command, RequestContext request) {
        return batchImport(command, request, null);
    }
}
