package edu.seu.vcampus.server.user.service;

import edu.seu.vcampus.common.user.TeacherAccountApplicationCommand;
import edu.seu.vcampus.common.user.UserView;
import edu.seu.vcampus.server.concurrency.ResourceKey;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.routing.ClientContext;
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
    private final UserAuditWriter auditWriter;

    TeacherAccountApplicationService(TransactionManager transactions, ResourceLockManager locks,
            UserRepository users, AuditRepository audits, PasswordHasher hasher) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.locks = Objects.requireNonNull(locks, "locks");
        this.users = Objects.requireNonNull(users, "users");
        this.audits = Objects.requireNonNull(audits, "audits");
        this.hasher = Objects.requireNonNull(hasher, "hasher");
        auditWriter = new UserAuditWriter(transactions, audits);
    }

    UserView apply(TeacherAccountApplicationCommand command) {
        return apply(command, null);
    }

    UserView apply(TeacherAccountApplicationCommand command, ClientContext context) {
        Objects.requireNonNull(command, "command");
        char[] password = command.password();
        try {
            return prepareAndApply(command.loginId(), password, address(context));
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private UserView prepareAndApply(String submittedLoginId, char[] password,
                                     String clientAddress) {
        String loginId;
        PasswordHash passwordHash;
        try {
            PasswordPolicy.validate(password);
            loginId = normalize(submittedLoginId);
            passwordHash = hasher.hash(password);
        } catch (RuntimeException error) {
            auditWriter.failure(null, "USER_REGISTER", null, error, clientAddress);
            throw error;
        }
        return locks.withLocks(List.of(new ResourceKey("LOGIN_ID", loginId)), () -> {
            try {
                return create(loginId, passwordHash, clientAddress);
            } catch (RuntimeException error) {
                // The business transaction has already rolled back. Retaining the
                // login lock serializes Access connection close/open during failure audit.
                auditWriter.failure(null, "USER_REGISTER", null, error, clientAddress);
                throw error;
            }
        });
    }

    private UserView create(String loginId, PasswordHash passwordHash, String clientAddress) {
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
            audits.record(connection, null, "USER_REGISTER", "USER", account.userId(),
                    "SUCCESS", clientAddress);
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

    private static String address(ClientContext context) {
        return context == null ? null : context.clientAddress();
    }
}
