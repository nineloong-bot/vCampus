package edu.seu.vcampus.server.user.service;

import edu.seu.vcampus.common.user.ChangePasswordCommand;
import edu.seu.vcampus.common.user.LoginCommand;
import edu.seu.vcampus.common.user.LoginResult;
import edu.seu.vcampus.common.user.UserView;
import edu.seu.vcampus.server.concurrency.ResourceKey;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.routing.ClientContext;
import edu.seu.vcampus.server.security.AccountDisabledException;
import edu.seu.vcampus.server.security.AccountLockedException;
import edu.seu.vcampus.server.security.AccountPendingException;
import edu.seu.vcampus.server.security.InvalidCredentialsException;
import edu.seu.vcampus.server.security.SessionExpiredException;
import edu.seu.vcampus.server.security.UserIdentity;
import edu.seu.vcampus.server.session.SessionRegistry;
import edu.seu.vcampus.server.user.domain.UserAccount;
import edu.seu.vcampus.server.user.repository.AuditRepository;
import edu.seu.vcampus.server.user.repository.PermissionRepository;
import edu.seu.vcampus.server.user.repository.UserRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Implements credential verification, lockout, sessions, logout, and password changes. */
final class AuthenticationService {
    private final TransactionManager transactions;
    private final ResourceLockManager locks;
    private final UserRepository users;
    private final AuditRepository audits;
    private final PasswordHasher hasher;
    private final SessionRegistry sessions;
    private final Clock clock;
    private final UserAuditWriter auditWriter;
    private final CredentialAuthenticator authenticator;
    private final UnknownLoginAttemptTracker unknownAttempts;

    AuthenticationService(TransactionManager transactions, ResourceLockManager locks,
            UserRepository users, PermissionRepository permissions, AuditRepository audits,
            PasswordHasher hasher, SessionRegistry sessions, Clock clock) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.locks = Objects.requireNonNull(locks, "locks");
        this.users = Objects.requireNonNull(users, "users");
        this.audits = Objects.requireNonNull(audits, "audits");
        this.hasher = Objects.requireNonNull(hasher, "hasher");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.clock = Objects.requireNonNull(clock, "clock");
        auditWriter = new UserAuditWriter(transactions, audits);
        authenticator = new CredentialAuthenticator(transactions, users, permissions,
                audits, hasher, clock);
        unknownAttempts = new UnknownLoginAttemptTracker(
                clock, CredentialAuthenticator.LOCKOUT_DURATION);
    }

    LoginResult login(LoginCommand command, ClientContext context) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(context, "context");
        char[] password = command.password();
        try {
            String loginId = normalize(command.loginId());
            UserAccount known = transactions.inTransaction(connection ->
                    users.findByNormalizedLoginId(connection, loginId).orElse(null));
            if (known == null) {
                RuntimeException error = loginFailure(unknownAttempts.recordFailure(loginId));
                auditWriter.failure(null, "USER_LOGIN", null, error, context.clientAddress());
                throw error;
            }
            try {
                return locks.withLocks(List.of(new ResourceKey("USER", known.userId())),
                        () -> toLoginResult(authenticator.authenticate(known.userId(), password,
                                context.clientAddress()), command.clientInstanceId(),
                                context.clientAddress()));
            } catch (RuntimeException error) {
                auditWriter.failure(null, "USER_LOGIN", known.userId(), error,
                        context.clientAddress());
                throw error;
            }
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    void logout(String token, ClientContext context) {
        sessions.revoke(token).ifPresent(identity -> transactions.inTransaction(connection -> {
            audits.record(connection, identity.userId(), "USER_LOGOUT", "USER",
                    identity.userId(), "SUCCESS", address(context));
            return null;
        }));
    }

    UserView currentUser(String token) {
        UserIdentity identity = sessions.requireSession(token);
        return transactions.inTransaction(connection -> users.findById(connection, identity.userId())
                .map(UserViews::from).orElseThrow(SessionExpiredException::new));
    }

    void changePassword(String token, ChangePasswordCommand command, ClientContext context) {
        Objects.requireNonNull(command, "command");
        UserIdentity identity = null;
        char[] oldPassword = command.oldPassword();
        char[] newPassword = command.newPassword();
        try {
            identity = sessions.requireSession(token);
            PasswordPolicy.validate(newPassword);
            String userId = identity.userId();
            boolean changed = locks.withLocks(List.of(new ResourceKey("USER", userId)),
                    () -> updatePassword(userId, oldPassword, newPassword, address(context)));
            if (!changed) throw new InvalidCredentialsException();
            sessions.revokeAllForUser(userId);
        } catch (RuntimeException error) {
            String userId = identity == null ? null : identity.userId();
            auditWriter.failure(userId, "USER_CHANGE_PASSWORD", userId, error,
                    address(context));
            throw error;
        } finally {
            Arrays.fill(oldPassword, '\0');
            Arrays.fill(newPassword, '\0');
        }
    }

    void revokeSessionsForUser(String userId) {
        locks.withLocks(List.of(new ResourceKey("USER", userId)), () -> {
            sessions.revokeAllForUser(userId);
            return null;
        });
    }

    private boolean updatePassword(String userId, char[] oldPassword, char[] newPassword,
                                   String clientAddress) {
        return transactions.inTransaction(connection -> {
            UserAccount account = users.findById(connection, userId).orElse(null);
            if (account == null || !hasher.verify(oldPassword, account.passwordHash(),
                    account.passwordSalt(), account.passwordIterations())) return false;
            PasswordHash replacement = hasher.hash(newPassword);
            LocalDateTime now = time(clock.instant());
            UserAccount updated = new UserAccount(account.userId(), account.loginId(),
                    replacement.hash(), replacement.salt(), replacement.iterations(),
                    account.role(), account.accountStatus(), false, 0, null,
                    account.lastLoginAt(), account.rowVersion(), account.createdAt(), now);
            users.updateWithVersion(connection, updated, account.rowVersion());
            audits.record(connection, userId, "USER_CHANGE_PASSWORD", "USER", userId,
                    "SUCCESS", clientAddress);
            return true;
        });
    }

    private LoginResult toLoginResult(
            CredentialAuthenticator.Attempt attempt, String clientInstanceId,
            String clientAddress) {
        if (attempt.errorCode() != null) throw loginFailure(attempt.errorCode());
        UserIdentity identity = UserViews.identity(attempt.account());
        int replaced = sessions.revokeAllForUserAndCount(identity.userId());
        String token = sessions.create(identity, attempt.permissions(),
                attempt.account().mustChangePassword(), clientInstanceId);
        if (replaced > 0) {
            auditWriter.bestEffort(identity.userId(), "USER_SESSION_REPLACED",
                    identity.userId(), "SUCCESS", clientAddress);
        }
        return new LoginResult(token, UserViews.from(attempt.account()), attempt.permissions(),
                attempt.account().mustChangePassword());
    }

    private static RuntimeException loginFailure(String code) {
        return switch (code) {
            case "AUTH_ACCOUNT_PENDING" -> new AccountPendingException();
            case "AUTH_ACCOUNT_DISABLED" -> new AccountDisabledException();
            case "AUTH_ACCOUNT_LOCKED" -> new AccountLockedException();
            default -> new InvalidCredentialsException();
        };
    }

    private static String address(ClientContext context) {
        return context == null ? null : context.clientAddress();
    }
    private static LocalDateTime time(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
    private static String normalize(String loginId) {
        return Objects.requireNonNull(loginId, "loginId").strip().toUpperCase(Locale.ROOT);
    }
}
