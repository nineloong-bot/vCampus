package edu.seu.vcampus.server.governance;

import edu.seu.vcampus.common.governance.AssignModuleAdministratorCommand;
import edu.seu.vcampus.common.governance.ModuleAdministrationSnapshot;
import edu.seu.vcampus.common.governance.RemoveModuleAdministratorCommand;
import edu.seu.vcampus.common.governance.SwapModuleAdministratorsCommand;
import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.server.concurrency.ResourceKey;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.session.SessionRegistry;
import edu.seu.vcampus.server.user.repository.AuditRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Governs the five dedicated module-administrator roles transactionally. */
public final class ModuleAdministrationService {
    private static final Map<String, UserRole> MODULE_ROLES = Map.of(
            "STUDENT", UserRole.STUDENT_ADMIN,
            "COURSE", UserRole.COURSE_ADMIN,
            "LIBRARY", UserRole.LIBRARY_ADMIN,
            "SHOP", UserRole.SHOP_ADMIN,
            "USER", UserRole.USER_ADMIN);
    private final TransactionManager transactions;
    private final ResourceLockManager locks;
    private final ModuleAdministrationRepository repository;
    private final AuditRepository audits;
    private final SessionRegistry sessions;

    /** Creates the service from existing transaction, lock, audit and session facilities. */
    public ModuleAdministrationService(TransactionManager transactions,
            ResourceLockManager locks, AccessModuleAdministrationRepository repository,
            AuditRepository audits, SessionRegistry sessions) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.locks = Objects.requireNonNull(locks, "locks");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.audits = Objects.requireNonNull(audits, "audits");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    /** Returns every account that currently carries a dedicated module role. */
    public ModuleAdministrationSnapshot list() {
        return transactions.inTransaction(connection ->
                new ModuleAdministrationSnapshot(repository.list(connection)));
    }

    /** Moves or reactivates an existing module administrator under a dedicated role. */
    public void assign(String actorUserId, AssignModuleAdministratorCommand command) {
        Objects.requireNonNull(command, "command");
        try {
            UserRole requestedRole = role(command.moduleCode());
            locked(assignKeys(command.moduleCode(), command.userId()), () -> {
                transactions.inTransaction(connection -> {
                    var account = repository.requireAccount(connection, command.userId(),
                            command.expectedModuleVersion());
                    requireGovernable(account.role());
                    if (account.status() == AccountStatus.ACTIVE
                            && account.role() != requestedRole
                            && repository.countActive(connection, account.role()) <= 1) {
                        throw lastAdministrator();
                    }
                    repository.updateRoleAndStatus(connection, account.userId(),
                            requestedRole, AccountStatus.ACTIVE, account.rowVersion());
                    audits.record(connection, actorUserId,
                            "GOVERNANCE_MODULE_ADMIN_ASSIGN",
                            "MODULE:" + command.moduleCode(), account.userId(), "SUCCESS");
                    return null;
                });
                sessions.revokeAllForUser(command.userId());
            });
        } catch (RuntimeException error) {
            auditFailure(actorUserId, "GOVERNANCE_MODULE_ADMIN_ASSIGN",
                    command.moduleCode(), command.userId(), error);
            throw error;
        }
    }

    /** Deactivates an administrator while preserving one active account for the module. */
    public void remove(String actorUserId, RemoveModuleAdministratorCommand command) {
        Objects.requireNonNull(command, "command");
        try {
            UserRole requiredRole = role(command.moduleCode());
            locked(keys(command.moduleCode(), command.userId()), () -> {
                transactions.inTransaction(connection -> {
                    var account = repository.requireAccount(connection, command.userId(),
                            command.expectedAssignmentVersion());
                    if (account.role() != requiredRole
                            || account.status() != AccountStatus.ACTIVE) {
                        throw new IllegalArgumentException("COMMON_VALIDATION_FAILED");
                    }
                    if (repository.countActive(connection, requiredRole) <= 1) {
                        throw lastAdministrator();
                    }
                    repository.updateRoleAndStatus(connection, account.userId(),
                            account.role(), AccountStatus.DISABLED, account.rowVersion());
                    audits.record(connection, actorUserId,
                            "GOVERNANCE_MODULE_ADMIN_REMOVE",
                            "MODULE:" + command.moduleCode(), account.userId(), "SUCCESS");
                    return null;
                });
                sessions.revokeAllForUser(command.userId());
            });
        } catch (RuntimeException error) {
            auditFailure(actorUserId, "GOVERNANCE_MODULE_ADMIN_REMOVE",
                    command.moduleCode(), command.userId(), error);
            throw error;
        }
    }

    /** Atomically exchanges two active administrators' dedicated module roles. */
    public void swap(String actorUserId, SwapModuleAdministratorsCommand command) {
        Objects.requireNonNull(command, "command");
        try {
            UserRole firstRole = role(command.firstModuleCode());
            UserRole secondRole = role(command.secondModuleCode());
            if (Objects.equals(command.firstUserId(), command.secondUserId())
                    || firstRole == secondRole) {
                throw new IllegalArgumentException("COMMON_VALIDATION_FAILED");
            }
            locked(List.of(key("MODULE", command.firstModuleCode()),
                    key("MODULE", command.secondModuleCode()),
                    key("USER", command.firstUserId()), key("USER", command.secondUserId())),
                    () -> {
                transactions.inTransaction(connection -> {
                    var first = repository.requireAccount(connection,
                            command.firstUserId(), command.firstExpectedVersion());
                    var second = repository.requireAccount(connection,
                            command.secondUserId(), command.secondExpectedVersion());
                    requireActiveRole(first, firstRole);
                    requireActiveRole(second, secondRole);
                    repository.updateRoleAndStatus(connection, first.userId(), secondRole,
                            AccountStatus.ACTIVE, first.rowVersion());
                    repository.updateRoleAndStatus(connection, second.userId(), firstRole,
                            AccountStatus.ACTIVE, second.rowVersion());
                    audits.record(connection, actorUserId,
                            "GOVERNANCE_MODULE_ADMIN_SWAP",
                            "MODULE:" + command.secondModuleCode(), first.userId(), "SUCCESS");
                    audits.record(connection, actorUserId,
                            "GOVERNANCE_MODULE_ADMIN_SWAP",
                            "MODULE:" + command.firstModuleCode(), second.userId(), "SUCCESS");
                    return null;
                });
                sessions.revokeAllForUser(command.firstUserId());
                sessions.revokeAllForUser(command.secondUserId());
            });
        } catch (RuntimeException error) {
            auditFailure(actorUserId, "GOVERNANCE_MODULE_ADMIN_SWAP",
                    command.firstModuleCode(), command.firstUserId(), error);
            throw error;
        }
    }

    /** Records a handler-level governance rejection without replacing its safe response. */
    public void auditRejected(String actorUserId, String actionCode,
                              String moduleCode, String targetUserId,
                              RuntimeException failure) {
        auditFailure(actorUserId, actionCode, moduleCode, targetUserId, failure);
    }

    private void auditFailure(String actor, String action, String module,
                              String target, RuntimeException failure) {
        try {
            transactions.inTransaction(connection -> {
                audits.record(connection, actor, action,
                        "MODULE:" + (module != null && MODULE_ROLES.containsKey(module)
                                ? module : "UNKNOWN"), target,
                        stableCode(failure));
                return null;
            });
        } catch (RuntimeException ignored) {
            // Failure audit is best-effort and must never replace the business error.
        }
    }

    private static String stableCode(RuntimeException failure) {
        if (failure instanceof java.util.ConcurrentModificationException) {
            return "COMMON_CONCURRENT_MODIFICATION";
        }
        if (failure instanceof IllegalArgumentException) return "COMMON_VALIDATION_FAILED";
        String message = failure.getMessage();
        return message != null && java.util.Set.of("AUTH_FORBIDDEN",
                "AUTH_SESSION_EXPIRED", "AUTH_SESSION_REVOKED_PASSWORD_RESET",
                "AUTH_INITIAL_PASSWORD_CHANGE_REQUIRED", "COMMON_VALIDATION_FAILED",
                "GOVERNANCE_LAST_MODULE_ADMIN_PROTECTED").contains(message)
                ? message : "COMMON_INTERNAL_ERROR";
    }

    private void locked(List<ResourceKey> resources, Runnable action) {
        List<ResourceKey> ordered = resources.stream().distinct()
                .sorted(Comparator.comparing(ResourceKey::resourceType)
                        .thenComparing(ResourceKey::resourceId)).toList();
        locks.withLocks(ordered, () -> { action.run(); return null; });
    }

    private static List<ResourceKey> keys(String module, String user) {
        return List.of(key("MODULE", module), key("USER", user));
    }

    private static List<ResourceKey> assignKeys(String targetModule, String user) {
        return List.of(key("MODULE", targetModule), key("USER", user),
                key("GOVERNANCE", "ASSIGN"));
    }

    private static ResourceKey key(String type, String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("COMMON_VALIDATION_FAILED");
        }
        return new ResourceKey(type, id);
    }

    private static UserRole role(String moduleCode) {
        UserRole role = moduleCode == null ? null : MODULE_ROLES.get(moduleCode.strip());
        if (role == null) throw new IllegalArgumentException("COMMON_VALIDATION_FAILED");
        return role;
    }

    private static void requireGovernable(UserRole role) {
        if (!MODULE_ROLES.containsValue(role)) {
            throw new IllegalArgumentException("COMMON_VALIDATION_FAILED");
        }
    }

    private static void requireActiveRole(
            ModuleAdministrationRepository.AdministratorAccount account,
            UserRole expectedRole) {
        if (account.role() != expectedRole || account.status() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("COMMON_VALIDATION_FAILED");
        }
    }

    private static IllegalStateException lastAdministrator() {
        return new IllegalStateException("GOVERNANCE_LAST_MODULE_ADMIN_PROTECTED");
    }
}
