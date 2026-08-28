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
import edu.seu.vcampus.common.shop.SuspendShopCommand;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.shop.domain.SellerApplication;
import edu.seu.vcampus.server.shop.domain.Shop;
import edu.seu.vcampus.server.shop.port.ShopUser;
import edu.seu.vcampus.server.shop.port.ShopUserKind;
import edu.seu.vcampus.server.shop.port.ShopUserPort;
import edu.seu.vcampus.server.shop.repository.ShopRepository;

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

    public ShopAdminService(ShopRepository repository, ShopUserPort users,
            TransactionManager transactions, ResourceLockManager locks, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.users = Objects.requireNonNull(users, "users");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.locks = Objects.requireNonNull(locks, "locks");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public PageResult<SellerApplicationView> searchApplications(SellerApplicationQuery query) {
        requireAdministrator();
        PageResult<SellerApplication> page = transactions.inTransaction(
                connection -> repository.searchApplications(connection, query));
        return new PageResult<>(page.items().stream().map(SellerApplicationService::toView).toList(),
                page.page(), page.pageSize(), page.total());
    }

    public SellerApplicationView reviewApplication(ReviewSellerApplicationCommand command) {
        Objects.requireNonNull(command, "command");
        ShopUser reviewer = requireAdministrator();
        SellerApplication snapshot = transactions.inTransaction(connection -> repository
                .findApplicationById(connection, command.applicationId())
                .orElseThrow(SellerApplicationService::invalidApplicationState));
        return locks.withLocks(List.of(
                SellerApplicationService.key("SELLER_APPLICATION", command.applicationId()),
                SellerApplicationService.key("USER", snapshot.applicantUserId())), () ->
                transactions.inTransaction(connection -> {
                    SellerApplication pending = repository.findApplicationById(connection,
                                    command.applicationId())
                            .orElseThrow(SellerApplicationService::invalidApplicationState);
                    if (pending.status() != SellerApplicationStatus.PENDING
                            || pending.rowVersion() != command.expectedVersion()) {
                        throw SellerApplicationService.invalidApplicationState();
                    }
                    if (command.decision() == SellerReviewDecision.REJECT) {
                        SellerApplicationService.requireText(command.reason(), "reason");
                        SellerApplication rejected = reviewed(pending, SellerApplicationStatus.REJECTED,
                                command.reason().strip(), reviewer.userId());
                        return SellerApplicationService.toView(repository.updateApplication(
                                connection, rejected, command.expectedVersion()));
                    }
                    if (command.decision() != SellerReviewDecision.APPROVE) {
                        throw new IllegalArgumentException("decision is required");
                    }
                    if (repository.findShopByOwner(connection, pending.applicantUserId()).isPresent()) {
                        throw SellerApplicationService.error(
                                ShopErrorCode.SHOP_SELLER_APPLICATION_EXISTS,
                                "Applicant already owns a shop");
                    }
                    var now = clock.instant();
                    Shop shop = new Shop(UUID.randomUUID().toString(), pending.applicantUserId(),
                            pending.shopName(), pending.description(), pending.category(), pending.contact(),
                            ShopStatus.ACTIVE, null, null, null, 0, now, now);
                    repository.insertShop(connection, shop);
                    SellerApplication approved = reviewed(pending, SellerApplicationStatus.APPROVED,
                            null, reviewer.userId());
                    return SellerApplicationService.toView(repository.updateApplication(
                            connection, approved, command.expectedVersion()));
                }));
    }

    public void suspendShop(SuspendShopCommand command) {
        Objects.requireNonNull(command, "command");
        ShopUser administrator = requireAdministrator();
        SellerApplicationService.requireText(command.reason(), "reason");
        transition(command.shopId(), ShopStatus.ACTIVE, ShopStatus.SUSPENDED,
                command.reason().strip(), administrator.userId(), command.expectedVersion());
    }

    public void resumeShop(ResumeShopCommand command) {
        Objects.requireNonNull(command, "command");
        requireAdministrator();
        SellerApplicationService.requireId(command.shopId(), "shopId");
        locks.withLocks(List.of(SellerApplicationService.key("SHOP", command.shopId())), () ->
                transactions.inTransaction(connection -> {
                    Shop existing = repository.findShopById(connection, command.shopId())
                            .orElseThrow(() -> SellerApplicationService.error(
                                    ShopErrorCode.SHOP_NOT_FOUND, "Shop does not exist"));
                    repository.updateShopStatus(connection, existing.shopId(), ShopStatus.SUSPENDED,
                            ShopStatus.ACTIVE, existing.suspensionReason(), existing.suspendedByUserId(),
                            existing.suspendedAt(), clock.instant(), command.expectedVersion());
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
                    return null;
                }));
    }

    private SellerApplication reviewed(SellerApplication pending,
            SellerApplicationStatus status, String reason, String reviewerId) {
        return new SellerApplication(pending.applicationId(), pending.applicantUserId(),
                pending.shopName(), pending.description(), pending.category(), pending.contact(),
                status, reason, reviewerId, pending.submittedAt(), clock.instant(),
                pending.rowVersion());
    }

    private ShopUser requireAdministrator() {
        ShopUser administrator = users.requireAdministrator();
        if (!administrator.active() || administrator.kind() != ShopUserKind.ADMINISTRATOR) {
            throw new SecurityException("Active administrator required");
        }
        return administrator;
    }
}
