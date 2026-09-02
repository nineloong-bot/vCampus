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

/** Seller-owned product, SKU, and shop-profile mutations. */
public final class ProductService {
    private final ShopRepository repository;
    private final ShopUserPort users;
    private final TransactionManager transactions;
    private final ResourceLockManager locks;
    private final Clock clock;

    public ProductService(ShopRepository repository, ShopUserPort users,
            TransactionManager transactions, ResourceLockManager locks, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.users = Objects.requireNonNull(users, "users");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.locks = Objects.requireNonNull(locks, "locks");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

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

    public ProductView getOwnedProduct(String sessionToken, String productId) {
        ShopUser actor = users.requireUser(sessionToken);
        Objects.requireNonNull(productId, "productId");
        return transactions.inTransaction(connection -> {
            Shop shop = requireOwnedShop(connection, actor.userId());
            return toView(connection, requireOwnedProduct(connection, productId, shop.shopId()));
        });
    }

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

    private Shop requireOwnedActiveShop(Connection connection, String ownerId) throws Exception {
        Shop shop = requireOwnedShop(connection, ownerId);
        if (shop.status() == ShopStatus.SUSPENDED) {
            throw SellerApplicationService.error(ShopErrorCode.SHOP_SUSPENDED, "Shop is suspended");
        }
        return shop;
    }

    private Shop requireOwnedShop(Connection connection, String ownerId) throws Exception {
        return repository.findShopByOwner(connection, ownerId)
                .orElseThrow(() -> SellerApplicationService.error(
                        ShopErrorCode.SHOP_SELLER_NOT_APPROVED, "Approved shop required"));
    }

    private Product requireOwnedProduct(Connection connection, String productId,
            String shopId) throws Exception {
        Product product = repository.findProductById(connection, productId)
                .orElseThrow(() -> SellerApplicationService.error(
                        ShopErrorCode.SHOP_PRODUCT_INACTIVE, "Product does not exist"));
        if (!product.shopId().equals(shopId)) {
            throw SellerApplicationService.error(ShopErrorCode.SHOP_NOT_OWNER,
                    "Product belongs to another shop");
        }
        return product;
    }

    private ProductView toView(Connection connection, Product product) throws Exception {
        return new ProductView(product.productId(), product.productName(), product.category(),
                product.description(), product.coverImageUrl(), product.status(), product.salesCount(), product.rowVersion(),
                repository.findSkusByProduct(connection, product.productId()).stream()
                        .map(ProductService::toSkuView).toList());
    }

    static ProductSkuView toSkuView(ProductSku sku) {
        return new ProductSkuView(sku.skuId(), sku.skuName(), sku.unitPrice(),
                sku.availableQuantity(), sku.stockQuantity(), sku.reservedQuantity(),
                sku.active(), sku.rowVersion());
    }

    private static ShopView toShopView(Shop shop) {
        return new ShopView(shop.shopId(), shop.ownerUserId(), shop.shopName(), shop.description(),
                shop.category(), shop.contact(), shop.status(), shop.suspensionReason(),
                shop.suspendedByUserId(), shop.suspendedAt(), shop.rowVersion());
    }

    private static void validateProduct(String name, String category, String description) {
        requireText(name, "productName");
        requireText(category, "category");
        requireText(description, "description");
    }

    private void requireProductNameAvailable(Connection connection, String shopId,
            String normalizedName, String productId) throws Exception {
        var existing = repository.findProductByNormalizedName(connection, shopId, normalizedName);
        if (existing.isPresent() && !existing.orElseThrow().productId().equals(productId)) {
            throw SellerApplicationService.error(ShopErrorCode.SHOP_PRODUCT_NAME_EXISTS,
                    "Product name already exists in this shop");
        }
    }

    private static String normalizeProductName(String name) {
        return name.strip().toLowerCase(Locale.ROOT);
    }

    private static String supportedCategory(String category) {
        try {
            return ShopCategories.requireSupported(category);
        } catch (IllegalArgumentException exception) {
            throw SellerApplicationService.error(ShopErrorCode.SHOP_CATEGORY_INVALID,
                    "Unsupported shop category");
        }
    }

    private static void validateSku(CreateSkuCommand sku) {
        validateSku(sku.skuName(), sku.unitPrice(), sku.stockQuantity());
    }

    private static void validateSku(String name, BigDecimal price, long stock) {
        requireText(name, "skuName");
        if (price == null || price.signum() < 0) {
            throw new IllegalArgumentException("unitPrice must be non-negative");
        }
        if (stock < 0) {
            throw new IllegalArgumentException("stockQuantity must be non-negative");
        }
    }

    private static void requireText(String value, String name) {
        SellerApplicationService.requireText(value, name);
    }
}
