package edu.seu.vcampus.server.shop.service;

import edu.seu.vcampus.common.shop.SaveSellerDraftCommand;
import edu.seu.vcampus.common.shop.SellerApplicationStatus;
import edu.seu.vcampus.common.shop.SellerApplicationView;
import edu.seu.vcampus.common.shop.ShopErrorCode;
import edu.seu.vcampus.common.shop.SubmitSellerApplicationCommand;
import edu.seu.vcampus.server.concurrency.ResourceKey;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.shop.ShopException;
import edu.seu.vcampus.server.shop.domain.SellerApplication;
import edu.seu.vcampus.server.shop.port.ShopUser;
import edu.seu.vcampus.server.shop.port.ShopUserPort;
import edu.seu.vcampus.server.shop.repository.ShopRepository;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Seller-owned application draft and submission workflow. */
public final class SellerApplicationService {
    private final ShopRepository repository;
    private final ShopUserPort users;
    private final TransactionManager transactions;
    private final ResourceLockManager locks;
    private final Clock clock;

    public SellerApplicationService(ShopRepository repository, ShopUserPort users,
            TransactionManager transactions, ResourceLockManager locks, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.users = Objects.requireNonNull(users, "users");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.locks = Objects.requireNonNull(locks, "locks");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public SellerApplicationView saveDraft(String sessionToken, SaveSellerDraftCommand command) {
        Objects.requireNonNull(command, "command");
        ShopUser actor = requireEligible(users.requireUser(sessionToken));
        requireProfile(command.shopName(), command.description(), command.category(), command.contact());
        return locks.withLocks(List.of(key("USER", actor.userId())), () ->
                transactions.inTransaction(connection -> {
                    if (command.applicationId() == null) {
                        if (command.expectedVersion() != 0
                                || repository.findApplicationByApplicant(connection, actor.userId()).isPresent()
                                || repository.findShopByOwner(connection, actor.userId()).isPresent()) {
                            throw error(ShopErrorCode.SHOP_SELLER_APPLICATION_EXISTS,
                                    "Seller application or approved shop already exists");
                        }
                        SellerApplication created = new SellerApplication(UUID.randomUUID().toString(),
                                actor.userId(), command.shopName().strip(), command.description().strip(),
                                command.category().strip(), command.contact().strip(),
                                command.applicationStatement(),
                                SellerApplicationStatus.DRAFT, null, null, null, null, 0);
                        return toView(repository.insertApplication(connection, created));
                    }
                    SellerApplication existing = requireApplication(connection, command.applicationId());
                    if (!existing.applicantUserId().equals(actor.userId())) {
                        throw error(ShopErrorCode.SHOP_NOT_OWNER, "Application is not owned by this user");
                    }
                    if ((existing.status() != SellerApplicationStatus.DRAFT
                            && existing.status() != SellerApplicationStatus.REJECTED)
                            || existing.rowVersion() != command.expectedVersion()) {
                        throw invalidApplicationState();
                    }
                    SellerApplication edited = new SellerApplication(existing.applicationId(),
                            existing.applicantUserId(), command.shopName().strip(),
                            command.description().strip(), command.category().strip(),
                            command.contact().strip(), command.applicationStatement(), SellerApplicationStatus.DRAFT,
                            null, null, existing.submittedAt(), null, existing.rowVersion());
                    return toView(repository.updateApplication(connection, edited,
                            command.expectedVersion()));
                }));
    }

    public SellerApplicationView submitApplication(String sessionToken,
            SubmitSellerApplicationCommand command) {
        Objects.requireNonNull(command, "command");
        ShopUser actor = requireEligible(users.requireUser(sessionToken));
        requireId(command.applicationId(), "applicationId");
        return locks.withLocks(List.of(key("SELLER_APPLICATION", command.applicationId())), () ->
                transactions.inTransaction(connection -> {
                    SellerApplication existing = requireApplication(connection, command.applicationId());
                    if (!existing.applicantUserId().equals(actor.userId())) {
                        throw error(ShopErrorCode.SHOP_NOT_OWNER, "Application is not owned by this user");
                    }
                    if (existing.status() != SellerApplicationStatus.DRAFT
                            || existing.rowVersion() != command.expectedVersion()) {
                        throw invalidApplicationState();
                    }
                    SellerApplication submitted = new SellerApplication(existing.applicationId(),
                            existing.applicantUserId(), existing.shopName(), existing.description(),
                            existing.category(), existing.contact(), existing.applicationStatement(), SellerApplicationStatus.PENDING,
                            null, null, clock.instant(), null, existing.rowVersion());
                    return toView(repository.updateApplication(connection, submitted,
                            command.expectedVersion()));
                }));
    }

    public SellerApplicationView getMyApplication(String sessionToken) {
        ShopUser actor = users.requireUser(sessionToken);
        return transactions.inTransaction(connection -> toView(repository
                .findApplicationByApplicant(connection, actor.userId())
                .orElseThrow(() -> error(ShopErrorCode.SHOP_SELLER_NOT_APPROVED,
                        "Seller application does not exist"))));
    }

    private SellerApplication requireApplication(java.sql.Connection connection,
            String applicationId) throws Exception {
        return repository.findApplicationById(connection, applicationId)
                .orElseThrow(SellerApplicationService::invalidApplicationState);
    }

    static SellerApplicationView toView(SellerApplication application) {
        return new SellerApplicationView(application.applicationId(), application.applicantUserId(),
                application.shopName(), application.description(), application.category(),
                application.contact(), application.applicationStatement(), application.status(), application.reviewReason(),
                application.reviewerUserId(), application.submittedAt(), application.reviewedAt(),
                application.rowVersion());
    }

    private static ShopUser requireEligible(ShopUser actor) {
        if (!actor.sellerEligible()) {
            throw new SecurityException("Active student or teacher account required");
        }
        return actor;
    }

    private static void requireProfile(String shopName, String description,
            String category, String contact) {
        requireText(shopName, "shopName");
        requireText(description, "description");
        requireText(category, "category");
        requireText(contact, "contact");
    }

    static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    static void requireId(String value, String name) {
        requireText(value, name);
    }

    static ResourceKey key(String type, String id) {
        return new ResourceKey(type, id);
    }

    static ShopException invalidApplicationState() {
        return error(ShopErrorCode.SHOP_SELLER_APPLICATION_STATUS_INVALID,
                "Seller application status or version is invalid");
    }

    static ShopException error(ShopErrorCode code, String message) {
        return new ShopException(code, message);
    }
}
