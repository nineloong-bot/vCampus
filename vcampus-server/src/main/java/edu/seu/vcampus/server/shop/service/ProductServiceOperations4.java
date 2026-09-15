package edu.seu.vcampus.server.shop.service;

import edu.seu.vcampus.common.shop.ChangeProductStatusCommand;
import edu.seu.vcampus.common.shop.CreateProductCommand;
import edu.seu.vcampus.common.shop.CreateSkuCommand;
import edu.seu.vcampus.common.shop.ProductSkuView;
import edu.seu.vcampus.common.shop.ProductStatus;
import edu.seu.vcampus.common.shop.ProductView;
import edu.seu.vcampus.common.shop.ShopErrorCode;
import edu.seu.vcampus.common.shop.ShopCategories;
import edu.seu.vcampus.common.shop.ShopStatus;
import edu.seu.vcampus.common.shop.ShopView;
import edu.seu.vcampus.common.shop.UpdateProductCommand;
import edu.seu.vcampus.common.shop.UpdateShopCommand;
import edu.seu.vcampus.common.shop.UpsertSkuCommand;
import edu.seu.vcampus.common.shop.ProductManagementQuery;
import edu.seu.vcampus.common.shop.ProductManagementSummary;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.shop.domain.Product;
import edu.seu.vcampus.server.shop.domain.ProductSku;
import edu.seu.vcampus.server.shop.domain.Shop;
import edu.seu.vcampus.server.shop.port.ShopUser;
import edu.seu.vcampus.server.shop.port.ShopUserPort;
import edu.seu.vcampus.server.shop.repository.ShopRepository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.Set;
import java.util.stream.Collectors;

/** Implements focused public operations for {@link ProductService}. */
abstract class ProductServiceOperations4 extends ProductServiceHelpers1 {
    protected ProductServiceOperations4(ShopRepository repository, ShopUserPort users,
            TransactionManager transactions, ResourceLockManager locks, Clock clock) {
        super(repository, users, transactions, locks, clock);
    }


    /**
     * Performs the change product status operation.
     * @param sessionToken the session token
     * @param command the command
     */
    public void changeProductStatus(String sessionToken, ChangeProductStatusCommand command) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(command.targetStatus(), "targetStatus");
        ShopUser actor = users.requireUser(sessionToken);
        locks.withLocks(List.of(SellerApplicationService.key("PRODUCT", command.productId())), () ->
                transactions.inTransaction(connection -> {
                    Shop shop = requireOwnedActiveShop(connection, actor.userId());
                    Product product = requireOwnedProduct(connection, command.productId(), shop.shopId());
                    boolean allowed = product.status() == ProductStatus.DRAFT
                            && command.targetStatus() == ProductStatus.INACTIVE
                            || product.status() == ProductStatus.INACTIVE
                            && command.targetStatus() == ProductStatus.ACTIVE
                            || product.status() == ProductStatus.ACTIVE
                            && command.targetStatus() == ProductStatus.INACTIVE;
                    if (!allowed) {
                        throw SellerApplicationService.error(ShopErrorCode.SHOP_STATUS_INVALID,
                                "Invalid seller product status transition");
                    }
                    var skus = repository.findSkusByProduct(connection, product.productId());
                    if (product.status() == ProductStatus.DRAFT
                            && skus.stream().noneMatch(sku -> sku.active())) {
                        throw SellerApplicationService.error(ShopErrorCode.SHOP_SKU_UNAVAILABLE,
                                "A completed draft requires an enabled SKU");
                    }
                    if (command.targetStatus() == ProductStatus.ACTIVE && skus.stream()
                            .noneMatch(sku -> sku.active() && sku.availableQuantity() > 0)) {
                        throw SellerApplicationService.error(ShopErrorCode.SHOP_SKU_UNAVAILABLE,
                                "An active product requires a sellable SKU");
                    }
                    repository.updateProductStatus(connection, product.productId(),
                            command.targetStatus(), clock.instant(), command.expectedVersion());
                    return null;
                }));
    }
}
