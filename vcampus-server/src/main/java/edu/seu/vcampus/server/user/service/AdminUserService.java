package edu.seu.vcampus.server.user.service;

import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.common.user.ChangeUserStatusCommand;
import edu.seu.vcampus.common.user.UpdateUserRoleCommand;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.common.user.UserSearchQuery;
import edu.seu.vcampus.common.user.UserSummary;
import edu.seu.vcampus.common.user.UserView;
import edu.seu.vcampus.server.concurrency.ResourceKey;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.user.domain.UserAccount;
import edu.seu.vcampus.server.user.repository.AuditRepository;
import edu.seu.vcampus.server.user.repository.UserRepository;

import java.util.List;
import java.util.Objects;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.function.Consumer;

/** Implements administrator account searching and guarded account modifications. */
final class AdminUserService {
    private final TransactionManager transactions;
    private final ResourceLockManager locks;
    private final UserRepository users;
    private final AuditRepository audits;
    private final Consumer<String> sessionRevoker;
    private final Clock clock;

    AdminUserService(TransactionManager transactions, ResourceLockManager locks, UserRepository users,
            AuditRepository audits, Consumer<String> sessionRevoker, Clock clock) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.locks = Objects.requireNonNull(locks, "locks");
        this.users = Objects.requireNonNull(users, "users");
        this.audits = Objects.requireNonNull(audits, "audits");
        this.sessionRevoker = Objects.requireNonNull(sessionRevoker, "sessionRevoker");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    PageResult<UserSummary> search(UserSearchQuery query) {
        Objects.requireNonNull(query, "query");
        return transactions.inTransaction(connection -> {
            PageResult<UserAccount> accounts = users.search(connection, query);
            return new PageResult<>(accounts.items().stream().map(AdminUserService::summary).toList(),
                    accounts.page(), accounts.pageSize(), accounts.total());
        });
    }

    UserView updateRole(String actorId, UpdateUserRoleCommand command) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(command, "command");
        return withAccountLocks(command.userId(), () -> {
            RoleUpdate result = transactions.inTransaction(connection -> {
                UserAccount account = account(connection, command.userId());
                if (command.newRole() == UserRole.STUDENT && account.role() != UserRole.STUDENT) {
                    throw new IllegalStateException("USER_ROLE_CONFLICT");
                }
                protectOnlyAdministrator(connection, account, command.newRole() != UserRole.ADMIN);
                boolean changed = account.role() != command.newRole();
                UserAccount updated = account.withRole(command.newRole(), now());
                users.updateWithVersion(connection, updated, command.expectedVersion());
                audits.record(connection, actorId, "USER_UPDATE_ROLE", "USER",
                        account.userId(), "SUCCESS");
                return new RoleUpdate(view(updated, command.expectedVersion() + 1), changed);
            });
            if (result.changed()) {
                sessionRevoker.accept(command.userId());
            }
            return result.view();
        });
    }

    UserView changeStatus(String actorId, ChangeUserStatusCommand command) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(command, "command");
        return withAccountLocks(command.userId(), () -> {
            UserView result = transactions.inTransaction(connection -> {
                UserAccount account = account(connection, command.userId());
                if (!canChangeStatus(account.accountStatus(), command.newStatus())) {
                    throw new IllegalStateException("USER_STATUS_CONFLICT");
                }
                protectOnlyAdministrator(connection, account, command.newStatus() != AccountStatus.ACTIVE);
                UserAccount updated = account.withStatus(command.newStatus(), now());
                users.updateWithVersion(connection, updated, command.expectedVersion());
                audits.record(connection, actorId, "USER_CHANGE_STATUS", "USER",
                        account.userId(), "SUCCESS");
                return view(updated, command.expectedVersion() + 1);
            });
            if (command.newStatus() == AccountStatus.DISABLED || command.newStatus() == AccountStatus.CANCELLED) {
                sessionRevoker.accept(command.userId());
            }
            return result;
        });
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), clock.getZone());
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

    private record RoleUpdate(UserView view, boolean changed) {
    }
}
