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

/** Holds dependencies and shared state for {@link StudentAdmissionCoordinator}. */
abstract class StudentAdmissionCoordinatorSupport implements StudentAdmissionService {
    protected static final String COMMAND = "STUDENT_CREATE";
    protected static final String MANUAL_COMMAND = "STUDENT_CREATE_MANUAL";
    protected final TransactionManager transactions;
    protected final ResourceLockManager locks;
    protected final RequestDeduplicator deduplicator;
    protected final OrganizationRepository organizations;
    protected final CampusCardNumberGenerator campusCards;
    protected final StudentNumberGenerator studentNumbers;
    protected final UserAccountProvisioningPort accounts;
    protected final StudentRepository students;
    protected final StudentChangeRepository changes;
    protected AdmissionFailureInjector failureInjector = AdmissionFailureInjector.NONE;

    /**
     * Creates a student admission coordinator with its required collaborators.
     * @param transactions the transactions
     * @param locks the locks
     * @param deduplicator the deduplicator
     * @param organizations the organizations
     * @param campusCards the campus cards
     * @param studentNumbers the student numbers
     * @param accounts the accounts
     * @param students the students
     * @param changes the changes
     */
    protected StudentAdmissionCoordinatorSupport(TransactionManager transactions, ResourceLockManager locks,
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
}
