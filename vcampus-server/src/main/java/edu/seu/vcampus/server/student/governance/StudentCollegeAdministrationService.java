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

import edu.seu.vcampus.common.student.governance.CreateCollegeAdministratorCommand;
import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.server.user.domain.UserAccount;
import edu.seu.vcampus.server.user.repository.AccessUserRepository;
import edu.seu.vcampus.server.user.repository.DuplicateLoginIdException;
import edu.seu.vcampus.server.user.repository.UserRepository;
import edu.seu.vcampus.server.user.service.PasswordHash;
import edu.seu.vcampus.server.user.service.PasswordHasher;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Maintains college administrators without permitting an unmanaged active college. */
public final class StudentCollegeAdministrationService {
    private final TransactionManager transactions;
    private final ResourceLockManager locks;
    private final StudentCollegeAdministrationRepository repository;
    private final AuditRepository audits;
    private final SessionRegistry sessions;
    private final UserRepository users;
    private final PasswordHasher hasher;

    /** Creates the service with existing transactional infrastructure. */
    public StudentCollegeAdministrationService(TransactionManager transactions,
            ResourceLockManager locks,
            StudentCollegeAdministrationRepository repository,
            AuditRepository audits, SessionRegistry sessions) {
        this(transactions, locks, repository, audits, sessions,
                new AccessUserRepository(), new PasswordHasher());
    }

    /** Creates the service with full user persistence and password hashing dependencies. */
    public StudentCollegeAdministrationService(TransactionManager transactions,
            ResourceLockManager locks,
            StudentCollegeAdministrationRepository repository,
            AuditRepository audits, SessionRegistry sessions,
            UserRepository users, PasswordHasher hasher) {
        this.transactions = Objects.requireNonNull(transactions);
        this.locks = Objects.requireNonNull(locks);
        this.repository = Objects.requireNonNull(repository);
        this.audits = Objects.requireNonNull(audits);
        this.sessions = Objects.requireNonNull(sessions);
        this.users = Objects.requireNonNull(users);
        this.hasher = Objects.requireNonNull(hasher);
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

    /** Provisions a new college administrator account. */
    public void createAdministrator(String actor, CreateCollegeAdministratorCommand command) {
        if (command == null || command.loginId() == null || command.loginId().isBlank()
                || command.password() == null || command.password().isBlank()) {
            throw new IllegalArgumentException("COMMON_VALIDATION_FAILED");
        }
        String loginId = command.loginId().trim();
        if (loginId.length() < 3 || loginId.length() > 32) {
            throw new IllegalArgumentException("用户名长度需在 3 到 32 位之间");
        }
        if (command.password().length() < 6) {
            throw new IllegalArgumentException("密码长度不能少于 6 位");
        }
        List<ResourceKey> keys;
        if (command.departmentId() != null && !command.departmentId().isBlank()) {
            keys = List.of(key("LOGIN_ID", loginId.toUpperCase(Locale.ROOT)),
                    key("DEPARTMENT", command.departmentId())).stream()
                    .sorted(Comparator.comparing(ResourceKey::resourceType)
                            .thenComparing(ResourceKey::resourceId)).toList();
        } else {
            keys = List.of(key("LOGIN_ID", loginId.toUpperCase(Locale.ROOT)));
        }

        withLocks(keys, () -> transactions.inTransaction(connection -> {
            if (users.findByNormalizedLoginId(connection, loginId).isPresent()) {
                throw new IllegalStateException("USER_LOGIN_ID_EXISTS");
            }
            if (command.departmentId() != null && !command.departmentId().isBlank()) {
                repository.requireActiveDepartment(connection, command.departmentId());
            }
            PasswordHash passwordHash = hasher.hash(command.password().toCharArray());
            String userId = UUID.randomUUID().toString();
            LocalDateTime now = LocalDateTime.now();
            UserAccount account = new UserAccount(
                    userId, loginId,
                    passwordHash.hash(), passwordHash.salt(), passwordHash.iterations(),
                    UserRole.COLLEGE_ADMIN, AccountStatus.ACTIVE, false,
                    0, null, null, 0, now, now);
            try {
                users.insert(connection, account);
            } catch (DuplicateLoginIdException error) {
                throw new IllegalStateException("USER_LOGIN_ID_EXISTS", error);
            }
            if (command.departmentId() != null && !command.departmentId().isBlank()) {
                repository.assign(connection, command.departmentId(), userId);
            }
            audits.record(connection, actor, "STUDENT_COLLEGE_ADMIN_CREATE",
                    "USER", userId, "SUCCESS");
            return null;
        }));
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
