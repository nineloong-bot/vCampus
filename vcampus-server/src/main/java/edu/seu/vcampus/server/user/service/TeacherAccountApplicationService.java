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

/** Creates locked, pending teacher-account applications. */
final class TeacherAccountApplicationService {
    private final TransactionManager transactions;
    private final ResourceLockManager locks;
    private final UserRepository users;
    private final AuditRepository audits;
    private final PasswordHasher hasher;

    TeacherAccountApplicationService(TransactionManager transactions, ResourceLockManager locks,
            UserRepository users, AuditRepository audits, PasswordHasher hasher) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.locks = Objects.requireNonNull(locks, "locks");
        this.users = Objects.requireNonNull(users, "users");
        this.audits = Objects.requireNonNull(audits, "audits");
        this.hasher = Objects.requireNonNull(hasher, "hasher");
    }

    UserView apply(TeacherAccountApplicationCommand command) {
        Objects.requireNonNull(command, "command");
        char[] password = command.password();
        try {
            PasswordPolicy.validate(password);
            String loginId = normalize(command.loginId());
            PasswordHash passwordHash = hasher.hash(password);
            return locks.withLocks(List.of(new ResourceKey("LOGIN_ID", loginId)),
                    () -> create(loginId, passwordHash));
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private UserView create(String loginId, PasswordHash passwordHash) {
        return transactions.inTransaction(connection -> {
            if (users.findByNormalizedLoginId(connection, loginId).isPresent()) {
                throw exists(null);
            }
            LocalDateTime now = LocalDateTime.now();
            UserAccount account = new UserAccount(UUID.randomUUID().toString(), loginId,
                    passwordHash.hash(), passwordHash.salt(), passwordHash.iterations(), TEACHER,
                    PENDING, false, 0, null, null, 0, now, now);
            try {
                users.insert(connection, account);
            } catch (DuplicateLoginIdException error) {
                throw exists(error);
            }
            audits.record(connection, account.userId(), "USER_REGISTER", "SUCCESS");
            return UserViews.from(account);
        });
    }

    private static String normalize(String loginId) {
        String normalized = Objects.requireNonNull(loginId, "loginId").strip().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9_]{4,32}")) {
            throw new IllegalArgumentException("COMMON_VALIDATION_FAILED");
        }
        return normalized;
    }

    private static IllegalStateException exists(Throwable cause) {
        return new IllegalStateException("USER_LOGIN_ID_EXISTS", cause);
    }
}
