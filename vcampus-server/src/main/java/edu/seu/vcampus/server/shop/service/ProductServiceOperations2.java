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
abstract class ProductServiceOperations2 extends ProductServiceOperations3 {
    protected ProductServiceOperations2(ShopRepository repository, ShopUserPort users,
            TransactionManager transactions, ResourceLockManager locks, Clock clock) {
        super(repository, users, transactions, locks, clock);
    }


    /**
     * Performs the create product operation.
     * @param sessionToken the session token
     * @param command the command
     * @return the operation result
     */
    public ProductView createProduct(String sessionToken, CreateProductCommand command) {
        Objects.requireNonNull(command, "command");
        validateProduct(command.productName(), command.category(), command.description());
        if (command.skus().isEmpty()) {
            throw new IllegalArgumentException("At least one SKU is required");
        }
        command.skus().forEach(ProductService::validateSku);
        ShopUser actor = users.requireUser(sessionToken);
        return locks.withLocks(List.of(SellerApplicationService.key("USER", actor.userId())), () ->
                transactions.inTransaction(connection -> {
                    Shop shop = requireOwnedActiveShop(connection, actor.userId());
                    String normalizedName = normalizeProductName(command.productName());
                    requireProductNameAvailable(connection, shop.shopId(), normalizedName, null);
                    var now = clock.instant();
                    Product product = repository.insertProduct(connection, new Product(
                            UUID.randomUUID().toString(), shop.shopId(), command.productName().strip(),
                            normalizedName, supportedCategory(shop.category()), command.description().strip(),
                            ProductImageUrl.validate(command.coverImageUrl(), command.category()),
                            ProductStatus.DRAFT, 0, 0, now, now));
                    for (CreateSkuCommand sku : command.skus()) {
                        repository.insertSku(connection, new ProductSku(UUID.randomUUID().toString(),
                                product.productId(), sku.skuName().strip(), sku.unitPrice(),
                                sku.stockQuantity(), 0, sku.active(), 0));
                    }
                    return toView(connection, product);
                }));
    }
}
