package edu.seu.vcampus.server.shop.service;

import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.shop.ResumeShopCommand;
import edu.seu.vcampus.common.shop.ReviewSellerApplicationCommand;
import edu.seu.vcampus.common.shop.SellerApplicationQuery;
import edu.seu.vcampus.common.shop.SellerApplicationStatus;
import edu.seu.vcampus.common.shop.SellerApplicationView;
import edu.seu.vcampus.common.shop.SellerReviewDecision;
import edu.seu.vcampus.common.shop.ShopErrorCode;
import edu.seu.vcampus.common.shop.ShopStatus;
import edu.seu.vcampus.common.shop.ShopAdminQuery;
import edu.seu.vcampus.common.shop.ShopAdminSummary;
import edu.seu.vcampus.common.shop.SuspendShopCommand;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.shop.domain.SellerApplication;
import edu.seu.vcampus.server.shop.domain.Shop;
import edu.seu.vcampus.server.shop.port.ShopUser;
import edu.seu.vcampus.server.shop.port.ShopUserPort;
import edu.seu.vcampus.server.shop.repository.ShopRepository;
import edu.seu.vcampus.server.shop.logging.ShopBusinessLogger;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Administrative seller review and shop-status workflow. */
public final class ShopAdminService {
    private final ShopRepository repository;
    private final ShopUserPort users;
    private final TransactionManager transactions;
    private final ResourceLockManager locks;
    private final Clock clock;
    private final ShopBusinessLogger businessLogger;

    public ShopAdminService(ShopRepository repository, ShopUserPort users,
            TransactionManager transactions, ResourceLockManager locks, Clock clock) {
        this(repository, users, transactions, locks, clock, new ShopBusinessLogger());
    }

    ShopAdminService(ShopRepository repository, ShopUserPort users,
            TransactionManager transactions, ResourceLockManager locks, Clock clock,
            ShopBusinessLogger businessLogger) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.users = Objects.requireNonNull(users, "users");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.locks = Objects.requireNonNull(locks, "locks");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.businessLogger = Objects.requireNonNull(businessLogger, "businessLogger");
    }

    public PageResult<SellerApplicationView> searchApplications(String sessionToken,
            SellerApplicationQuery query) {
        requireAdministrator(sessionToken);
        PageResult<SellerApplication> page = transactions.inTransaction(
                connection -> repository.searchApplications(connection, query));
        return new PageResult<>(page.items().stream().map(SellerApplicationService::toView).toList(),
                page.page(), page.pageSize(), page.total());
    }

    public PageResult<ShopAdminSummary> searchShops(String sessionToken, ShopAdminQuery query) {
        requireAdministrator(sessionToken);
        return transactions.inTransaction(connection -> repository.searchShops(connection, query));
    }

    public SellerApplicationView reviewApplication(String sessionToken,
            ReviewSellerApplicationCommand command) {
        Objects.requireNonNull(command, "command");
        ShopUser reviewer = requireAdministrator(sessionToken);
        SellerApplication snapshot = transactions.inTransaction(connection -> repository
                .findApplicationById(connection, command.applicationId())
                .orElseThrow(SellerApplicationService::invalidApplicationState));
        return locks.withLocks(List.of(
                SellerApplicationService.key("SELLER_APPLICATION", command.applicationId()),
                SellerApplicationService.key("USER", snapshot.applicantUserId()),
                SellerApplicationService.key("SHOP_NAME", normalizeShopName(snapshot.shopName()))), () ->
                transactions.inTransaction(connection -> {
                    SellerApplication pending = repository.findApplicationById(connection,
                                    command.applicationId())
                            .orElseThrow(SellerApplicationService::invalidApplicationState);
                    if (pending.status() != SellerApplicationStatus.PENDING
                            || pending.rowVersion() != command.expectedVersion()) {
                        throw SellerApplicationService.error(ShopErrorCode.SHOP_CONCURRENT_MODIFICATION,
                                "Seller application changed before review");
                    }
                    if (command.decision() == SellerReviewDecision.REJECT) {
                        SellerApplicationService.requireText(command.reason(), "reason");
                        SellerApplication rejected = reviewed(pending, SellerApplicationStatus.REJECTED,
                                command.reason().strip(), reviewer.userId());
                        SellerApplicationView result = SellerApplicationService.toView(repository.updateApplication(
                                connection, rejected, command.expectedVersion()));
                        businessLogger.stateChanged(reviewer.userId(), "SELLER_APPLICATION",
                                pending.applicationId(), pending.status().name(), rejected.status().name(),
                                rejected.reviewReason());
                        return result;
                    }
                    if (command.decision() != SellerReviewDecision.APPROVE) {
                        throw new IllegalArgumentException("decision is required");
                    }
                    if (repository.findShopByOwner(connection, pending.applicantUserId()).isPresent()) {
                        throw SellerApplicationService.error(
                                ShopErrorCode.SHOP_SELLER_APPLICATION_EXISTS,
                                "Applicant already owns a shop");
                    }
                    if (repository.findShopByNormalizedName(connection,
                            normalizeShopName(pending.shopName())).isPresent()) {
                        throw SellerApplicationService.error(ShopErrorCode.SHOP_NAME_EXISTS,
                                "Shop name already exists");
                    }
                    var now = clock.instant();
                    Shop shop = new Shop(UUID.randomUUID().toString(), pending.applicantUserId(),
                            pending.shopName(), normalizeShopName(pending.shopName()), pending.description(), pending.category(), pending.contact(),
                            ShopStatus.ACTIVE, null, null, null, 0, now, now);
                    repository.insertShop(connection, shop);
                    SellerApplication approved = reviewed(pending, SellerApplicationStatus.APPROVED,
                            null, reviewer.userId());
                    SellerApplicationView result = SellerApplicationService.toView(repository.updateApplication(
                            connection, approved, command.expectedVersion()));
                    businessLogger.stateChanged(reviewer.userId(), "SELLER_APPLICATION",
                            pending.applicationId(), pending.status().name(), approved.status().name(), null);
                    return result;
                }));
    }

    public void suspendShop(String sessionToken, SuspendShopCommand command) {
        Objects.requireNonNull(command, "command");
        ShopUser administrator = requireAdministrator(sessionToken);
        SellerApplicationService.requireText(command.reason(), "reason");
        transition(command.shopId(), ShopStatus.ACTIVE, ShopStatus.SUSPENDED,
                command.reason().strip(), administrator.userId(), command.expectedVersion());
    }

    public void resumeShop(String sessionToken, ResumeShopCommand command) {
        Objects.requireNonNull(command, "command");
        ShopUser administrator = requireAdministrator(sessionToken);
        SellerApplicationService.requireId(command.shopId(), "shopId");
        locks.withLocks(List.of(SellerApplicationService.key("SHOP", command.shopId())), () ->
                transactions.inTransaction(connection -> {
                    Shop existing = repository.findShopById(connection, command.shopId())
                            .orElseThrow(() -> SellerApplicationService.error(
                                    ShopErrorCode.SHOP_NOT_FOUND, "Shop does not exist"));
                    repository.updateShopStatus(connection, existing.shopId(), ShopStatus.SUSPENDED,
                            ShopStatus.ACTIVE, null, null, null, clock.instant(), command.expectedVersion());
                    businessLogger.stateChanged(administrator.userId(), "SHOP", existing.shopId(),
                            ShopStatus.SUSPENDED.name(), ShopStatus.ACTIVE.name(), null);
                    return null;
                }));
    }

    private void transition(String shopId, ShopStatus expected, ShopStatus target,
            String reason, String administratorId, long version) {
        SellerApplicationService.requireId(shopId, "shopId");
        locks.withLocks(List.of(SellerApplicationService.key("SHOP", shopId)), () ->
                transactions.inTransaction(connection -> {
                    if (repository.findShopById(connection, shopId).isEmpty()) {
                        throw SellerApplicationService.error(ShopErrorCode.SHOP_NOT_FOUND,
                                "Shop does not exist");
                    }
                    repository.updateShopStatus(connection, shopId, expected, target, reason,
                            administratorId, clock.instant(), clock.instant(), version);
                    businessLogger.stateChanged(administratorId, "SHOP", shopId,
                            expected.name(), target.name(), reason);
                    return null;
                }));
    }

    private SellerApplication reviewed(SellerApplication pending,
            SellerApplicationStatus status, String reason, String reviewerId) {
        return new SellerApplication(pending.applicationId(), pending.applicantUserId(),
                pending.shopName(), pending.description(), pending.category(), pending.contact(),
                pending.applicationStatement(),
                status, reason, reviewerId, pending.submittedAt(), clock.instant(),
                pending.rowVersion());
    }

    private ShopUser requireAdministrator(String sessionToken) {
        return users.requireAdministrator(sessionToken);
    }

    private static String normalizeShopName(String shopName) {
        return shopName.strip().toLowerCase(java.util.Locale.ROOT);
    }
}
