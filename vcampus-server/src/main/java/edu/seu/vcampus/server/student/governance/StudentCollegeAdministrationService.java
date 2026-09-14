package edu.seu.vcampus.server.student.governance;

import edu.seu.vcampus.common.student.governance.AssignStudentCollegeAdministratorCommand;
import edu.seu.vcampus.common.student.governance.DeactivateStudentCollegeAdministratorCommand;
import edu.seu.vcampus.common.student.governance.StudentCollegeAdministrationSnapshot;
import edu.seu.vcampus.common.student.governance.TransferStudentCollegeAdministratorCommand;
import edu.seu.vcampus.server.concurrency.ResourceKey;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.session.SessionRegistry;
import edu.seu.vcampus.server.user.repository.AuditRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Maintains college administrators without permitting an unmanaged active college. */
public final class StudentCollegeAdministrationService {
    private final TransactionManager transactions;
    private final ResourceLockManager locks;
    private final StudentCollegeAdministrationRepository repository;
    private final AuditRepository audits;
    private final SessionRegistry sessions;

    /** Creates the service with existing transactional infrastructure. */
    public StudentCollegeAdministrationService(TransactionManager transactions,
            ResourceLockManager locks,
            AccessStudentCollegeAdministrationRepository repository,
            AuditRepository audits, SessionRegistry sessions) {
        this.transactions = Objects.requireNonNull(transactions);
        this.locks = Objects.requireNonNull(locks);
        this.repository = Objects.requireNonNull(repository);
        this.audits = Objects.requireNonNull(audits);
        this.sessions = Objects.requireNonNull(sessions);
    }

    /** Returns all college-administrator accounts and assignable departments. */
    public StudentCollegeAdministrationSnapshot list() {
        return transactions.inTransaction(connection ->
                new StudentCollegeAdministrationSnapshot(
                        repository.listAdministrators(connection),
                        repository.listDepartments(connection)));
    }

    /** Assigns an unassigned preset college administrator. */
    public void assign(String actor, AssignStudentCollegeAdministratorCommand command) {
        require(command.departmentId(), command.userId());
        withLocks(List.of(key("DEPARTMENT", command.departmentId()),
                key("USER", command.userId())), () -> transactions.inTransaction(connection -> {
            repository.requireDepartment(connection, command.departmentId(),
                    command.expectedDepartmentVersion());
            repository.requireAdministrator(connection, command.userId());
            repository.requireUnassigned(connection, command.userId());
            repository.assign(connection, command.departmentId(), command.userId());
            audits.record(connection, actor, "STUDENT_COLLEGE_ADMIN_ASSIGN",
                    "USER", command.userId(), "SUCCESS");
            return null;
        }));
        sessions.revokeAllForUser(command.userId());
    }

    /** Deactivates a college administrator while protecting the final assignment. */
    public void deactivate(String actor,
            DeactivateStudentCollegeAdministratorCommand command) {
        require(command.departmentId(), command.userId());
        withLocks(List.of(key("DEPARTMENT", command.departmentId()),
                key("USER", command.userId())), () -> transactions.inTransaction(connection -> {
            repository.requireAssignment(connection, command.departmentId(),
                    command.userId(), command.expectedAssignmentVersion());
            if (repository.countActive(connection, command.departmentId()) <= 1) {
                throw new IllegalStateException("STUDENT_LAST_COLLEGE_ADMIN_PROTECTED");
            }
            repository.deactivate(connection, command.departmentId(), command.userId(),
                    command.expectedAssignmentVersion());
            audits.record(connection, actor, "STUDENT_COLLEGE_ADMIN_DEACTIVATE",
                    "USER", command.userId(), "SUCCESS");
            return null;
        }));
        sessions.revokeAllForUser(command.userId());
    }

    /** Atomically transfers one administrator between colleges. */
    public void transfer(String actor,
            TransferStudentCollegeAdministratorCommand command) {
        require(command.sourceDepartmentId(), command.userId());
        require(command.targetDepartmentId(), command.userId());
        if (command.sourceDepartmentId().equals(command.targetDepartmentId())) {
            throw new IllegalArgumentException("COMMON_VALIDATION_FAILED");
        }
        List<ResourceKey> keys = List.of(
                key("DEPARTMENT", command.sourceDepartmentId()),
                key("DEPARTMENT", command.targetDepartmentId()),
                key("USER", command.userId())).stream().sorted(Comparator
                        .comparing(ResourceKey::resourceType)
                        .thenComparing(ResourceKey::resourceId)).toList();
        withLocks(keys, () -> transactions.inTransaction(connection -> {
            repository.requireAssignment(connection, command.sourceDepartmentId(),
                    command.userId(), command.expectedAssignmentVersion());
            repository.requireDepartment(connection, command.targetDepartmentId(),
                    command.expectedTargetDepartmentVersion());
            if (repository.countActive(connection, command.sourceDepartmentId()) <= 1) {
                throw new IllegalStateException("STUDENT_LAST_COLLEGE_ADMIN_PROTECTED");
            }
            repository.transfer(connection, command.sourceDepartmentId(),
                    command.targetDepartmentId(), command.userId(),
                    command.expectedAssignmentVersion());
            audits.record(connection, actor, "STUDENT_COLLEGE_ADMIN_TRANSFER",
                    "USER", command.userId(), "SUCCESS");
            return null;
        }));
        sessions.revokeAllForUser(command.userId());
    }

    private <T> T withLocks(List<ResourceKey> keys,
            java.util.function.Supplier<T> action) {
        return locks.withLocks(keys, action);
    }

    private static ResourceKey key(String type, String id) {
        return new ResourceKey(type, id);
    }

    private static void require(String department, String user) {
        if (department == null || department.isBlank()
                || user == null || user.isBlank()) {
            throw new IllegalArgumentException("COMMON_VALIDATION_FAILED");
        }
    }
}
