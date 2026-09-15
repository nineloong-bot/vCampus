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
abstract class StudentAdmissionCoordinatorOperations2 extends StudentAdmissionCoordinatorHelpers1 {
    protected StudentAdmissionCoordinatorOperations2(TransactionManager transactions, ResourceLockManager locks,
            RequestDeduplicator deduplicator, OrganizationRepository organizations,
            CampusCardNumberGenerator campusCards, StudentNumberGenerator studentNumbers,
            UserAccountProvisioningPort accounts, StudentRepository students,
            StudentChangeRepository changes) {
        super(transactions, locks, deduplicator, organizations, campusCards, studentNumbers, accounts, students, changes);
    }


    @Override
    public BatchImportResult batchImport(BatchImportCommand command, RequestContext request,
            String trustedDepartmentId) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(request, "request");
        if (command.classIds() == null || command.classIds().isEmpty())
            throw new StudentAdmissionException("STUDENT_BATCH_NO_CLASSES", "至少需要一个班级");
        if (command.entries() == null || command.entries().isEmpty())
            throw new StudentAdmissionException("STUDENT_BATCH_NO_ENTRIES", "至少需要一条学生记录");
        for (int i = 0; i < command.entries().size(); i++) {
            var e = command.entries().get(i);
            if (e.classIndex() < 0 || e.classIndex() >= command.classIds().size())
                throw new StudentAdmissionException("STUDENT_BATCH_INVALID_CLASS_INDEX",
                        "第 " + (i + 1) + " 条记录的班级索引无效");
        }
        return transactions.inTransaction(connection -> batchImportInTransaction(
                new TransactionContext(connection, request.userId(), request.clientInstanceId()),
                command, request, trustedDepartmentId));
    }
}
