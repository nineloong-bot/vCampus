package edu.seu.vcampus.server.user.service;

import edu.seu.vcampus.common.user.TeacherAccountApplicationCommand;
import edu.seu.vcampus.common.user.UserView;
import edu.seu.vcampus.server.concurrency.ResourceKey;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.user.domain.UserAccount;
import edu.seu.vcampus.server.user.repository.AuditRepository;
import edu.seu.vcampus.server.user.repository.DuplicateLoginIdException;
import edu.seu.vcampus.server.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import static edu.seu.vcampus.common.user.AccountStatus.PENDING;
import static edu.seu.vcampus.common.user.UserRole.TEACHER;

/** Coordinates password hashing, locking, persistence, and auditing for users. */
public final class UserServiceImpl implements UserService {
    private static final String PASSWORD_POLICY_ERROR = "AUTH_PASSWORD_POLICY_VIOLATION";
    private static final String LOGIN_EXISTS_ERROR = "USER_LOGIN_ID_EXISTS";
    private final TransactionManager transactions;
    private final ResourceLockManager locks;
    private final UserRepository users;
    private final AuditRepository audits;
    private final PasswordHasher hasher;

    /** Creates the service from existing transaction, lock, and repository boundaries. */
    public UserServiceImpl(
            TransactionManager transactions,
            ResourceLockManager locks,
            UserRepository users,
            AuditRepository audits,
            PasswordHasher hasher) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.locks = Objects.requireNonNull(locks, "locks");
        this.users = Objects.requireNonNull(users, "users");
        this.audits = Objects.requireNonNull(audits, "audits");
        this.hasher = Objects.requireNonNull(hasher, "hasher");
    }

    /**
     * Creates one pending teacher application and clears the submitted password
     * array whether the operation succeeds or fails.
     */
    @Override
    public UserView applyForTeacherAccount(TeacherAccountApplicationCommand command) {
        Objects.requireNonNull(command, "command");
        char[] password = command.password();
        try {
            validatePassword(password);
            String normalizedLoginId = normalizeLoginId(command.loginId());
            PasswordHash passwordHash = hasher.hash(password);
            return locks.withLocks(
                    List.of(new ResourceKey("LOGIN_ID", normalizedLoginId)),
                    () -> createTeacher(normalizedLoginId, passwordHash));
        } finally {
            if (password != null) {
                Arrays.fill(password, '\0');
            }
        }
    }

    private UserView createTeacher(String normalizedLoginId, PasswordHash passwordHash) {
        return transactions.inTransaction(connection -> {
            if (users.findByNormalizedLoginId(connection, normalizedLoginId).isPresent()) {
                throw loginExists(null);
            }
            LocalDateTime now = LocalDateTime.now();
            UserAccount account = new UserAccount(
                    UUID.randomUUID().toString(), normalizedLoginId,
                    passwordHash.hash(), passwordHash.salt(), passwordHash.iterations(),
                    TEACHER, PENDING, false, 0, null, null, 0, now, now);
            try {
                users.insert(connection, account);
            } catch (DuplicateLoginIdException error) {
                throw loginExists(error);
            }
            audits.record(connection, account.userId(), "USER_REGISTER", "SUCCESS");
            return toView(account);
        });
    }

    private static void validatePassword(char[] password) {
        if (password == null || password.length < 8 || password.length > 64) {
            throw new IllegalArgumentException(PASSWORD_POLICY_ERROR);
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (char character : password) {
            hasLetter |= Character.isLetter(character);
            hasDigit |= Character.isDigit(character);
        }
        if (!hasLetter || !hasDigit) {
            throw new IllegalArgumentException(PASSWORD_POLICY_ERROR);
        }
    }

    private static String normalizeLoginId(String loginId) {
        String normalized = Objects.requireNonNull(loginId, "loginId")
                .strip().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9_]{4,32}")) {
            throw new IllegalArgumentException("COMMON_VALIDATION_FAILED");
        }
        return normalized;
    }

    private static IllegalStateException loginExists(Throwable cause) {
        return new IllegalStateException(LOGIN_EXISTS_ERROR, cause);
    }

    private static UserView toView(UserAccount account) {
        return new UserView(account.userId(), account.loginId(), account.role(),
                account.accountStatus(), account.mustChangePassword(),
                account.lastLoginAt(), account.rowVersion(), account.createdAt(),
                account.updatedAt());
    }
}
