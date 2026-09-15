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
abstract class ProductServiceOperations3 extends ProductServiceOperations4 {
    protected ProductServiceOperations3(ShopRepository repository, ShopUserPort users,
            TransactionManager transactions, ResourceLockManager locks, Clock clock) {
        super(repository, users, transactions, locks, clock);
    }


    /**
     * Performs the update product operation.
     * @param sessionToken the session token
     * @param command the command
     * @return the operation result
     */
    public ProductView updateProduct(String sessionToken, UpdateProductCommand command) {
        Objects.requireNonNull(command, "command");
        ShopUser actor = users.requireUser(sessionToken);
        return locks.withLocks(List.of(SellerApplicationService.key("PRODUCT", command.productId())), () ->
                transactions.inTransaction(connection -> {
                    Shop shop = requireOwnedActiveShop(connection, actor.userId());
                    Product existing = requireOwnedProduct(connection, command.productId(), shop.shopId());
                    validateProduct(command.productName(), command.category(), command.description());
                    String normalizedName = normalizeProductName(command.productName());
                    requireProductNameAvailable(connection, shop.shopId(), normalizedName, existing.productId());
                    for (UpsertSkuCommand sku : command.skus()) {
                        validateSku(sku.skuName(), sku.unitPrice(), sku.stockQuantity());
                    }
                    Product updated = repository.updateProduct(connection, new Product(
                            existing.productId(), existing.shopId(), command.productName().strip(),
                            normalizedName, supportedCategory(shop.category()), command.description().strip(),
                            ProductImageUrl.validate(command.coverImageUrl(), command.category()), existing.status(),
                            existing.salesCount(), existing.rowVersion(), existing.createdAt(), clock.instant()),
                            command.expectedVersion());
                    List<ProductSku> existingSkus = repository.findSkusByProduct(connection,
                            existing.productId());
                    Set<String> retainedSkuIds = command.skus().stream()
                            .map(UpsertSkuCommand::skuId).filter(Objects::nonNull)
                            .collect(Collectors.toSet());
                    for (UpsertSkuCommand commandSku : command.skus()) {
                        if (commandSku.skuId() == null) {
                            repository.insertSku(connection, new ProductSku(UUID.randomUUID().toString(),
                                    existing.productId(), commandSku.skuName().strip(), commandSku.unitPrice(),
                                    commandSku.stockQuantity(), 0, commandSku.active(), 0));
                        } else {
                            ProductSku stored = existingSkus.stream()
                                    .filter(sku -> sku.skuId().equals(commandSku.skuId()))
                                    .findFirst().orElseThrow(() -> SellerApplicationService.error(
                                            ShopErrorCode.SHOP_SKU_UNAVAILABLE, "SKU is not part of product"));
                            if (commandSku.stockQuantity() < stored.reservedQuantity()) {
                                throw new IllegalArgumentException("Stock cannot be below reserved quantity");
                            }
                            repository.updateSku(connection, new ProductSku(stored.skuId(),
                                    stored.productId(), commandSku.skuName().strip(), commandSku.unitPrice(),
                                    commandSku.stockQuantity(), stored.reservedQuantity(),
                                    commandSku.active(), stored.rowVersion()), commandSku.expectedVersion());
                        }
                    }
                    for (ProductSku omitted : existingSkus) {
                        if (retainedSkuIds.contains(omitted.skuId()) || !omitted.active()) continue;
                        if (omitted.reservedQuantity() > 0) {
                            throw SellerApplicationService.error(ShopErrorCode.SHOP_SKU_UNAVAILABLE,
                                    "Reserved SKU cannot be removed");
                        }
                        repository.updateSku(connection, new ProductSku(omitted.skuId(),
                                omitted.productId(), omitted.skuName(), omitted.unitPrice(),
                                omitted.stockQuantity(), omitted.reservedQuantity(), false,
                                omitted.rowVersion()), omitted.rowVersion());
                    }
                    return toView(connection, updated);
                }));
    }
}
