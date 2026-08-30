package edu.seu.vcampus.server.user.service;

import edu.seu.vcampus.server.concurrency.ResourceKey;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionContext;
import edu.seu.vcampus.server.user.domain.UserAccount;
import edu.seu.vcampus.server.user.repository.AuditRepository;
import edu.seu.vcampus.server.user.repository.DuplicateLoginIdException;
import edu.seu.vcampus.server.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static edu.seu.vcampus.common.user.AccountStatus.ACTIVE;
import static edu.seu.vcampus.common.user.UserRole.STUDENT;

/** Creates student accounts inside transactions owned by an admission coordinator. */
public final class UserAccountProvisioningService implements UserAccountProvisioningPort {
    private static final String CAMPUS_CARD_PATTERN = "^2[123]3[0-9]{6}$";
    private static final String LOGIN_EXISTS_ERROR = "USER_LOGIN_ID_EXISTS";
    private final ResourceLockManager locks;
    private final UserRepository users;
    private final AuditRepository audits;
    private final PasswordHasher hasher;

    /** Creates the service from existing lock, persistence, audit, and hashing boundaries. */
    public UserAccountProvisioningService(
            ResourceLockManager locks,
            UserRepository users,
            AuditRepository audits,
            PasswordHasher hasher) {
        this.locks = Objects.requireNonNull(locks, "locks");
        this.users = Objects.requireNonNull(users, "users");
        this.audits = Objects.requireNonNull(audits, "audits");
        this.hasher = Objects.requireNonNull(hasher, "hasher");
    }

    /**
     * Creates one active student account without managing the supplied transaction
     * and clears the initial password whether the operation succeeds or fails.
     */
    @Override
    public ProvisionedUserAccount createStudentAccount(
            TransactionContext transaction,
            String campusCardNumber,
            char[] initialPassword) {
        try {
            Objects.requireNonNull(transaction, "transaction");
            requireCampusCardFormat(campusCardNumber);
            Objects.requireNonNull(initialPassword, "initialPassword");
            return locks.withLocks(
                    List.of(new ResourceKey("LOGIN_ID", campusCardNumber)),
                    () -> createAccount(transaction, campusCardNumber, initialPassword));
        } finally {
            if (initialPassword != null) {
                Arrays.fill(initialPassword, '\0');
            }
        }
    }

    private ProvisionedUserAccount createAccount(
            TransactionContext transaction,
            String campusCardNumber,
            char[] initialPassword) {
        if (users.findByNormalizedLoginId(
                transaction.connection(), campusCardNumber).isPresent()) {
            throw loginExists(null);
        }
        PasswordHash passwordHash = hasher.hash(initialPassword);
        LocalDateTime now = LocalDateTime.now();
        UserAccount account = new UserAccount(
                UUID.randomUUID().toString(), campusCardNumber,
                passwordHash.hash(), passwordHash.salt(), passwordHash.iterations(),
                STUDENT, ACTIVE, true, 0, null, null, 0, now, now);
        try {
            users.insert(transaction.connection(), account);
        } catch (DuplicateLoginIdException error) {
            throw loginExists(error);
        }
        audits.record(transaction.connection(), transaction.userId(),
                "STUDENT_ACCOUNT_PROVISIONED", "USER", account.userId(),
                "SUCCESS", null);
        return new ProvisionedUserAccount(
                account.userId(), account.loginId(), account.role(),
                account.accountStatus());
    }

    private static void requireCampusCardFormat(String campusCardNumber) {
        if (campusCardNumber == null || !campusCardNumber.matches(CAMPUS_CARD_PATTERN)) {
            throw new IllegalArgumentException("COMMON_VALIDATION_FAILED");
        }
    }

    private static IllegalStateException loginExists(Throwable cause) {
        return new IllegalStateException(LOGIN_EXISTS_ERROR, cause);
    }
}
