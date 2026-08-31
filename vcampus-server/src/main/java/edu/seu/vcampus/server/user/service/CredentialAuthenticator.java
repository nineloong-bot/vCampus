package edu.seu.vcampus.server.user.service;

import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.user.domain.UserAccount;
import edu.seu.vcampus.server.user.repository.AuditRepository;
import edu.seu.vcampus.server.user.repository.PermissionRepository;
import edu.seu.vcampus.server.user.repository.UserRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Set;

import static edu.seu.vcampus.common.user.AccountStatus.ACTIVE;

/** Verifies one credential attempt and updates lockout state atomically. */
final class CredentialAuthenticator {
    // 30 秒是团队确认的课程演示与调试锁定策略。
    static final Duration LOCKOUT_DURATION = Duration.ofSeconds(30);
    private final TransactionManager transactions;
    private final UserRepository users;
    private final PermissionRepository permissions;
    private final AuditRepository audits;
    private final PasswordHasher hasher;
    private final Clock clock;

    CredentialAuthenticator(TransactionManager transactions, UserRepository users,
            PermissionRepository permissions, AuditRepository audits,
            PasswordHasher hasher, Clock clock) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.users = Objects.requireNonNull(users, "users");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.audits = Objects.requireNonNull(audits, "audits");
        this.hasher = Objects.requireNonNull(hasher, "hasher");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    Attempt authenticate(String userId, char[] password, String clientAddress) {
        return transactions.inTransaction(connection -> {
            UserAccount account = users.findById(connection, userId).orElse(null);
            if (account == null) return Attempt.failure("AUTH_INVALID_CREDENTIALS");
            String blocked = lockedCode(account);
            if (blocked != null) return Attempt.failure(blocked);
            if (!hasher.verify(password, account.passwordHash(), account.passwordSalt(),
                    account.passwordIterations())) {
                return Attempt.failure(recordFailure(connection, account));
            }
            blocked = unavailableStatusCode(account);
            if (blocked != null) return Attempt.failure(blocked);
            Instant now = clock.instant();
            UserAccount updated = authenticationUpdate(account, 0, null, time(now), now);
            users.updateWithVersion(connection, updated, account.rowVersion());
            Set<String> granted = permissions.findByRole(connection, updated.role());
            audits.record(connection, updated.userId(), "USER_LOGIN", "USER",
                    updated.userId(), "SUCCESS", clientAddress);
            return Attempt.success(updated, granted);
        });
    }

    private String recordFailure(java.sql.Connection connection, UserAccount account) {
        if (account.lockedUntil() == null || !account.lockedUntil().isAfter(time(clock.instant()))) {
            int failures = account.failedLoginCount() + 1;
            Instant now = clock.instant();
            LocalDateTime lockedUntil = failures >= 5
                    ? time(now.plus(LOCKOUT_DURATION)) : account.lockedUntil();
            users.updateWithVersion(connection, authenticationUpdate(account, failures,
                    lockedUntil, account.lastLoginAt(), now), account.rowVersion());
            return failures >= 5 ? "AUTH_ACCOUNT_LOCKED" : "AUTH_INVALID_CREDENTIALS";
        }
        return "AUTH_ACCOUNT_LOCKED";
    }

    private String unavailableStatusCode(UserAccount account) {
        if (account.accountStatus() != ACTIVE) {
            return account.accountStatus().name().equals("PENDING")
                    ? "AUTH_ACCOUNT_PENDING" : "AUTH_ACCOUNT_DISABLED";
        }
        return null;
    }

    private String lockedCode(UserAccount account) {
        return account.lockedUntil() != null
                && account.lockedUntil().isAfter(time(clock.instant()))
                ? "AUTH_ACCOUNT_LOCKED" : null;
    }

    private static UserAccount authenticationUpdate(UserAccount account, int failures,
            LocalDateTime lockedUntil, LocalDateTime lastLoginAt, Instant now) {
        return new UserAccount(account.userId(), account.loginId(), account.passwordHash(),
                account.passwordSalt(), account.passwordIterations(), account.role(),
                account.accountStatus(), account.mustChangePassword(), failures, lockedUntil,
                lastLoginAt, account.rowVersion(), account.createdAt(), time(now));
    }

    private static LocalDateTime time(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    /** Credential result; only success is audited in this transaction. */
    record Attempt(UserAccount account, Set<String> permissions, String errorCode) {
        static Attempt success(UserAccount account, Set<String> permissions) {
            return new Attempt(account, Set.copyOf(permissions), null);
        }
        static Attempt failure(String code) { return new Attempt(null, Set.of(), code); }
    }
}
