package edu.seu.vcampus.server.user.service;

import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.common.user.ChangeUserStatusCommand;
import edu.seu.vcampus.common.user.ResetStudentPasswordCommand;
import edu.seu.vcampus.common.user.ResetTeacherPasswordCommand;
import edu.seu.vcampus.common.user.UpdateUserRoleCommand;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.common.user.UserSearchQuery;
import edu.seu.vcampus.common.user.UserSummary;
import edu.seu.vcampus.common.user.UserView;
import edu.seu.vcampus.server.concurrency.ResourceKey;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.routing.ClientContext;
import edu.seu.vcampus.server.user.domain.UserAccount;
import edu.seu.vcampus.server.user.repository.AuditRepository;
import edu.seu.vcampus.server.user.repository.UserRepository;

import java.util.List;
import java.util.Objects;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.time.LocalDateTime;
import java.util.function.Consumer;

/** Implements administrator account searching and guarded account modifications. */
final class AdminUserService {
    private final TransactionManager transactions;
    private final ResourceLockManager locks;
    private final UserRepository users;
    private final AuditRepository audits;
    private final PasswordHasher hasher;
    private final Consumer<String> sessionRevoker;
    private final Consumer<String> passwordResetSessionRevoker;
    private final UserAuditWriter auditWriter;

    AdminUserService(TransactionManager transactions, ResourceLockManager locks, UserRepository users,
            AuditRepository audits, PasswordHasher hasher,
            Consumer<String> sessionRevoker, Consumer<String> passwordResetSessionRevoker) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.locks = Objects.requireNonNull(locks, "locks");
        this.users = Objects.requireNonNull(users, "users");
        this.audits = Objects.requireNonNull(audits, "audits");
        this.hasher = Objects.requireNonNull(hasher, "hasher");
        this.sessionRevoker = Objects.requireNonNull(sessionRevoker, "sessionRevoker");
        this.passwordResetSessionRevoker = Objects.requireNonNull(
                passwordResetSessionRevoker, "passwordResetSessionRevoker");
        auditWriter = new UserAuditWriter(transactions, audits);
    }

    PageResult<UserSummary> search(UserSearchQuery query) {
        Objects.requireNonNull(query, "query");
        return transactions.inTransaction(connection -> {
            PageResult<UserAccount> accounts = users.search(connection, query);
            return new PageResult<>(accounts.items().stream().map(AdminUserService::summary).toList(),
                    accounts.page(), accounts.pageSize(), accounts.total());
        });
    }

    UserView updateRole(UpdateUserRoleCommand command) {
        return updateRole(null, command, null);
    }

    UserView updateRole(String actorUserId, UpdateUserRoleCommand command,
                        ClientContext context) {
        Objects.requireNonNull(command, "command");
        IllegalArgumentException retired =
                new IllegalArgumentException("COMMON_VALIDATION_FAILED");
        auditWriter.failure(actorUserId, "USER_UPDATE_ROLE", command.userId(),
                retired, address(context));
        throw retired;
    }

    UserView resetStudentPassword(String actorUserId,
            ResetStudentPasswordCommand command, ClientContext context) {
        Objects.requireNonNull(command, "command");
        return resetPassword(actorUserId, command.targetUserId(),
                command.expectedRowVersion(), UserRole.STUDENT, context);
    }

    UserView resetTeacherPassword(String actorUserId,
            ResetTeacherPasswordCommand command, ClientContext context) {
        Objects.requireNonNull(command, "command");
        return resetPassword(actorUserId, command.targetUserId(),
                command.expectedRowVersion(), UserRole.TEACHER, context);
    }

    private UserView resetPassword(String actorUserId, String targetUserId,
            long expectedRowVersion, UserRole requiredRole, ClientContext context) {
        try {
            return locks.withLocks(
                    List.of(new ResourceKey("USER", targetUserId)), () ->
                    {
                        UserView result = transactions.inTransaction(connection -> {
                                UserAccount account = account(connection, targetUserId);
                                if (account.role() != requiredRole) {
                                    throw new IllegalArgumentException(
                                            "COMMON_VALIDATION_FAILED");
                                }
                                if (account.rowVersion() != expectedRowVersion) {
                                    throw new ConcurrentModificationException(
                                            "User account version is stale");
                                }
                                char[] initialPassword = {'1', '2', '3', '4', '5', '6', '7', '8'};
                                try {
                                    PasswordHash password = hasher.hash(initialPassword);
                                    UserAccount updated = reset(account, password);
                                    users.updateWithVersion(connection, updated,
                                            expectedRowVersion);
                                    audits.record(connection, actorUserId,
                                            "USER_PASSWORD_RESET", "USER",
                                            account.userId(), "SUCCESS", address(context));
                                    return view(updated, expectedRowVersion + 1);
                                } finally {
                                    Arrays.fill(initialPassword, '\0');
                                }
                            });
                        passwordResetSessionRevoker.accept(targetUserId);
                        return result;
                    });
        } catch (RuntimeException error) {
            auditWriter.failure(actorUserId, "USER_PASSWORD_RESET",
                    targetUserId, error, address(context));
            throw error;
        }
    }

    UserView changeStatus(ChangeUserStatusCommand command) {
        return changeStatus(null, command, null);
    }

    UserView changeStatus(String actorUserId, ChangeUserStatusCommand command,
                          ClientContext context) {
        Objects.requireNonNull(command, "command");
        try {
            return withAccountLocks(command.userId(), () -> {
                UserView result = transactions.inTransaction(connection -> {
                    UserAccount account = account(connection, command.userId());
                    if (!canChangeStatus(account.accountStatus(), command.newStatus())) {
                        throw new IllegalStateException("USER_STATUS_CONFLICT");
                    }
                    protectOnlyAdministrator(connection, account,
                            command.newStatus() != AccountStatus.ACTIVE);
                    UserAccount updated = account.withStatus(command.newStatus());
                    users.updateWithVersion(connection, updated, command.expectedVersion());
                    audits.record(connection, actorUserId, "USER_CHANGE_STATUS", "USER",
                            account.userId(), "SUCCESS", address(context));
                    return view(updated, command.expectedVersion() + 1);
                });
                if (command.newStatus() == AccountStatus.DISABLED
                        || command.newStatus() == AccountStatus.CANCELLED) {
                    sessionRevoker.accept(command.userId());
                }
                return result;
            });
        } catch (RuntimeException error) {
            auditWriter.failure(actorUserId, "USER_CHANGE_STATUS", command.userId(),
                    error, address(context));
            throw error;
        }
    }

    private <T> T withAccountLocks(String userId, java.util.function.Supplier<T> action) {
        return locks.withLocks(List.of(new ResourceKey("ADMIN", "ACTIVE"),
                new ResourceKey("USER", userId)), action);
    }

    private UserAccount account(java.sql.Connection connection, String userId) {
        return users.findById(connection, userId)
                .orElseThrow(() -> new IllegalStateException("USER_NOT_FOUND"));
    }

    private void protectOnlyAdministrator(java.sql.Connection connection, UserAccount account, boolean removesAdmin) {
        if (removesAdmin && account.role() == UserRole.ADMIN && account.accountStatus() == AccountStatus.ACTIVE
                && users.countActiveAdministrators(connection) <= 1) {
            throw new IllegalStateException("USER_LAST_ADMIN_PROTECTED");
        }
    }

    private static boolean canChangeStatus(AccountStatus from, AccountStatus to) {
        return (from == AccountStatus.PENDING && (to == AccountStatus.ACTIVE || to == AccountStatus.CANCELLED))
                || (from == AccountStatus.ACTIVE && (to == AccountStatus.DISABLED || to == AccountStatus.CANCELLED))
                || (from == AccountStatus.DISABLED && to == AccountStatus.ACTIVE);
    }

    private static UserSummary summary(UserAccount account) {
        return new UserSummary(account.userId(), account.loginId(), account.role(), account.accountStatus(),
                account.lastLoginAt(), account.rowVersion());
    }

    private static UserView view(UserAccount account, long version) {
        return new UserView(account.userId(), account.loginId(), account.role(), account.accountStatus(),
                account.mustChangePassword(), account.lastLoginAt(), version, account.createdAt(), account.updatedAt());
    }

    private static UserAccount reset(UserAccount account, PasswordHash password) {
        return new UserAccount(account.userId(), account.loginId(), password.hash(),
                password.salt(), password.iterations(), account.role(),
                account.accountStatus(), true, 0, null, account.lastLoginAt(),
                account.rowVersion(), account.createdAt(), LocalDateTime.now());
    }

    private static String address(ClientContext context) {
        return context == null ? null : context.clientAddress();
    }
}
