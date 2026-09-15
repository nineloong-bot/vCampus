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
abstract class ProductServiceOperations1 extends ProductServiceOperations2 {
    protected ProductServiceOperations1(ShopRepository repository, ShopUserPort users,
            TransactionManager transactions, ResourceLockManager locks, Clock clock) {
        super(repository, users, transactions, locks, clock);
    }


    /**
     * Performs the update shop operation.
     * @param sessionToken the session token
     * @param command the command
     * @return the operation result
     */
    public ShopView updateShop(String sessionToken, UpdateShopCommand command) {
        Objects.requireNonNull(command, "command");
        ShopUser actor = users.requireUser(sessionToken);
        requireText(command.shopName(), "shopName");
        requireText(command.description(), "description");
        requireText(command.category(), "category");
        requireText(command.contact(), "contact");
        String normalizedName = command.shopName().strip().toLowerCase(Locale.ROOT);
        return locks.withLocks(List.of(SellerApplicationService.key("USER", actor.userId()),
                SellerApplicationService.key("SHOP_NAME", normalizedName)), () ->
                transactions.inTransaction(connection -> {
                    Shop owned = requireOwnedActiveShop(connection, actor.userId());
                    var nameOwner = repository.findShopByNormalizedName(connection, normalizedName);
                    if (nameOwner.isPresent() && !nameOwner.orElseThrow().shopId().equals(owned.shopId())) {
                        throw SellerApplicationService.error(ShopErrorCode.SHOP_NAME_EXISTS,
                                "Shop name already exists");
                    }
                    Shop updated = new Shop(owned.shopId(), owned.ownerUserId(),
                            command.shopName().strip(), normalizedName, command.description().strip(),
                            owned.category(), command.contact().strip(), owned.status(),
                            owned.suspensionReason(), owned.suspendedByUserId(), owned.suspendedAt(),
                            owned.rowVersion(), owned.createdAt(), clock.instant());
                    return toShopView(repository.updateShopProfile(connection, updated,
                            command.expectedVersion()));
                }));
    }

    /**
     * Performs the search owned products operation.
     * @param sessionToken the session token
     * @param query the query
     * @return the operation result
     */
    public PageResult<ProductManagementSummary> searchOwnedProducts(String sessionToken,
            ProductManagementQuery query) {
        Objects.requireNonNull(query, "query");
        ShopUser actor = users.requireUser(sessionToken);
        return transactions.inTransaction(connection -> {
            Shop shop = requireOwnedShop(connection, actor.userId());
            return repository.searchManagedProducts(connection, new ProductManagementQuery(
                    shop.shopId(), query.status(), query.keyword(), query.pageNumber(), query.pageSize()));
        });
    }

    /**
     * Performs the get owned product operation.
     * @param sessionToken the session token
     * @param productId the product identifier
     * @return the operation result
     */
    public ProductView getOwnedProduct(String sessionToken, String productId) {
        ShopUser actor = users.requireUser(sessionToken);
        Objects.requireNonNull(productId, "productId");
        return transactions.inTransaction(connection -> {
            Shop shop = requireOwnedShop(connection, actor.userId());
            return toView(connection, requireOwnedProduct(connection, productId, shop.shopId()));
        });
    }
}
