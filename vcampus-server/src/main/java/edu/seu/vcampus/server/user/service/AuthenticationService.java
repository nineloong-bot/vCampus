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
import edu.seu.vcampus.server.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import static edu.seu.vcampus.common.user.AccountStatus.ACTIVE;
/** Implements credential verification, lockout, sessions, logout, and password changes. */
final class AuthenticationService {
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);
    private final TransactionManager transactions;
    private final ResourceLockManager locks;
    private final UserRepository users;
    private final AuditRepository audits;
    private final PasswordHasher hasher;
    private final SessionRegistry sessions;
    private final Clock clock;
    AuthenticationService(TransactionManager transactions, ResourceLockManager locks,
            UserRepository users, AuditRepository audits, PasswordHasher hasher,
            SessionRegistry sessions, Clock clock) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.locks = Objects.requireNonNull(locks, "locks");
        this.users = Objects.requireNonNull(users, "users");
        this.audits = Objects.requireNonNull(audits, "audits");
        this.hasher = Objects.requireNonNull(hasher, "hasher");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.clock = Objects.requireNonNull(clock, "clock");
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
                auditUnknownLogin();
                throw new InvalidCredentialsException();
            }
            return locks.withLocks(List.of(new ResourceKey("USER", known.userId())),
                    () -> toLoginResult(authenticate(known.userId(), password)));
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    void logout(String token) {
        sessions.revoke(token).ifPresent(identity -> transactions.inTransaction(connection -> {
            audits.record(connection, identity.userId(), "USER_LOGOUT", "SUCCESS");
            return null;
        }));
    }

    UserView currentUser(String token) {
        UserIdentity identity = sessions.requireSession(token);
        return transactions.inTransaction(connection -> users.findById(connection, identity.userId())
                .map(UserViews::from).orElseThrow(SessionExpiredException::new));
    }

    void changePassword(String token, ChangePasswordCommand command) {
        Objects.requireNonNull(command, "command");
        UserIdentity identity = sessions.requireSession(token);
        char[] oldPassword = command.oldPassword();
        char[] newPassword = command.newPassword();
        try {
            PasswordPolicy.validate(newPassword);
            boolean changed = locks.withLocks(List.of(new ResourceKey("USER", identity.userId())), () -> {
                if (!updatePassword(identity.userId(), oldPassword, newPassword)) return false;
                sessions.revokeAllForUser(identity.userId());
                return true;
            });
            if (!changed) {
                throw new InvalidCredentialsException();
            }
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

    private Attempt authenticate(String userId, char[] password) {
        return transactions.inTransaction(connection -> {
            UserAccount account = users.findById(connection, userId).orElse(null);
            if (account == null || !hasher.verify(password, account.passwordHash(), account.passwordSalt(),
                    account.passwordIterations())) {
                if (account != null) {
                    recordFailure(connection, account);
                }
                return Attempt.INVALID;
            }
            Instant now = clock.instant();
            if (account.accountStatus() != ACTIVE) {
                return account.accountStatus().name().equals("PENDING") ? Attempt.PENDING : Attempt.DISABLED;
            }
            if (account.lockedUntil() != null && account.lockedUntil().isAfter(time(now))) {
                return Attempt.LOCKED;
            }
            UserAccount updated = authenticationUpdate(account, 0, null, time(now), now);
            users.updateWithVersion(connection, updated, account.rowVersion());
            audits.record(connection, account.userId(), "USER_LOGIN", "SUCCESS");
            return new Attempt(updated, null);
        });
    }

    private void recordFailure(java.sql.Connection connection, UserAccount account) {
        if (account.lockedUntil() != null && account.lockedUntil().isAfter(time(clock.instant()))) {
            audits.record(connection, account.userId(), "USER_LOGIN", "AUTH_INVALID_CREDENTIALS");
            return;
        }
        int failures = account.failedLoginCount() + 1;
        Instant now = clock.instant();
        LocalDateTime lockedUntil = failures >= 5 ? time(now.plus(LOCKOUT_DURATION)) : account.lockedUntil();
        UserAccount updated = authenticationUpdate(account, failures, lockedUntil,
                account.lastLoginAt(), now);
        users.updateWithVersion(connection, updated, account.rowVersion());
        audits.record(connection, account.userId(), "USER_LOGIN", "AUTH_INVALID_CREDENTIALS");
    }

    private boolean updatePassword(String userId, char[] oldPassword, char[] newPassword) {
        return transactions.inTransaction(connection -> {
            UserAccount account = users.findById(connection, userId).orElse(null);
            if (account == null || !hasher.verify(oldPassword, account.passwordHash(), account.passwordSalt(),
                    account.passwordIterations())) {
                return false;
            }
            PasswordHash replacement = hasher.hash(newPassword);
            LocalDateTime now = time(clock.instant());
            UserAccount updated = new UserAccount(account.userId(), account.loginId(), replacement.hash(),
                    replacement.salt(), replacement.iterations(), account.role(), account.accountStatus(), false,
                    0, null, account.lastLoginAt(), account.rowVersion(), account.createdAt(), now);
            users.updateWithVersion(connection, updated, account.rowVersion());
            audits.record(connection, account.userId(), "USER_CHANGE_PASSWORD", "SUCCESS");
            return true;
        });
    }

    private LoginResult toLoginResult(Attempt attempt) {
        if (attempt == Attempt.INVALID) throw new InvalidCredentialsException();
        if (attempt == Attempt.PENDING) throw new AccountPendingException();
        if (attempt == Attempt.DISABLED) throw new AccountDisabledException();
        if (attempt == Attempt.LOCKED) throw new AccountLockedException();
        UserIdentity identity = UserViews.identity(attempt.account);
        return new LoginResult(sessions.create(identity), UserViews.from(attempt.account),
                identity.permissions(), identity.restricted());
    }
    private void auditUnknownLogin() {
        transactions.inTransaction(connection -> {
            audits.record(connection, null, "USER_LOGIN", "AUTH_INVALID_CREDENTIALS");
            return null;
        });
    }

    private static UserAccount authenticationUpdate(UserAccount account, int failures,
            LocalDateTime lockedUntil, LocalDateTime lastLoginAt, Instant now) {
        return new UserAccount(account.userId(), account.loginId(), account.passwordHash(),
                account.passwordSalt(), account.passwordIterations(), account.role(), account.accountStatus(),
                account.mustChangePassword(), failures, lockedUntil, lastLoginAt, account.rowVersion(),
                account.createdAt(), time(now));
    }

    private static LocalDateTime time(Instant instant) { return LocalDateTime.ofInstant(instant, ZoneOffset.UTC); }
    private static String normalize(String loginId) { return Objects.requireNonNull(loginId, "loginId").strip().toUpperCase(Locale.ROOT); }

    private record Attempt(UserAccount account, String error) {
        private static final Attempt INVALID = new Attempt(null, "INVALID");
        private static final Attempt PENDING = new Attempt(null, "PENDING");
        private static final Attempt DISABLED = new Attempt(null, "DISABLED");
        private static final Attempt LOCKED = new Attempt(null, "LOCKED");
    }
}
