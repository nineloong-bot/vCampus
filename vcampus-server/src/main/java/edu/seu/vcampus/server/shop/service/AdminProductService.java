package edu.seu.vcampus.server.shop.service;

import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.shop.*;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.shop.domain.Product;
import edu.seu.vcampus.server.shop.domain.ProductSku;
import edu.seu.vcampus.server.shop.domain.Shop;
import edu.seu.vcampus.server.shop.logging.ShopBusinessLogger;
import edu.seu.vcampus.server.shop.port.ShopUserPort;
import edu.seu.vcampus.server.shop.repository.ShopRepository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Clock;
import java.util.*;
import java.util.stream.Collectors;

/** Administrator-scoped product management for an explicitly selected shop. */
public final class AdminProductService {
    private final ShopRepository repository;
    private final ShopUserPort users;
    private final TransactionManager transactions;
    private final ResourceLockManager locks;
    private final Clock clock;
    private final ShopBusinessLogger log;

    public AdminProductService(ShopRepository repository, ShopUserPort users,
            TransactionManager transactions, ResourceLockManager locks, Clock clock,
            ShopBusinessLogger log) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.users = Objects.requireNonNull(users, "users");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.locks = Objects.requireNonNull(locks, "locks");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.log = Objects.requireNonNull(log, "log");
    }

    public PageResult<ProductManagementSummary> searchProducts(String sessionToken,
            ProductManagementQuery query) {
        users.requireAdministrator(sessionToken);
        Objects.requireNonNull(query, "query");
        return transactions.inTransaction(connection -> {
            requireShop(connection, query.shopId());
            return repository.searchManagedProducts(connection, query);
        });
    }

    public ProductView createProduct(String sessionToken, AdminCreateProductCommand request) {
        var actor = users.requireAdministrator(sessionToken);
        Objects.requireNonNull(request, "request");
        CreateProductCommand command = Objects.requireNonNull(request.command(), "command");
        validateProduct(command.productName(), command.description(), command.skus().size());
        command.skus().forEach(AdminProductService::validateSku);
        return locks.withLocks(List.of(SellerApplicationService.key("SHOP", request.shopId())), () ->
                transactions.inTransaction(connection -> {
                    Shop shop = requireShop(connection, request.shopId());
                    String normalized = normalize(command.productName());
                    requireNameAvailable(connection, shop.shopId(), normalized, null);
                    var now = clock.instant();
                    Product product = repository.insertProduct(connection, new Product(
                            UUID.randomUUID().toString(), shop.shopId(), command.productName().strip(),
                            normalized, shop.category(), command.description().strip(),
                            ProductImageUrl.validate(command.coverImageUrl()), ProductStatus.DRAFT,
                            0, 0, now, now));
                    for (CreateSkuCommand sku : command.skus()) repository.insertSku(connection,
                            new ProductSku(UUID.randomUUID().toString(), product.productId(),
                                    sku.skuName().strip(), sku.unitPrice(), sku.stockQuantity(), 0,
                                    sku.active(), 0));
                    log.productChanged(actor.userId(), shop.shopId(), product.productId(),
                            "CREATED");
                    return toView(connection, product);
                }));
    }

    public ProductView updateProduct(String sessionToken, AdminUpdateProductCommand request) {
        var actor = users.requireAdministrator(sessionToken);
        Objects.requireNonNull(request, "request");
        UpdateProductCommand command = Objects.requireNonNull(request.command(), "command");
        validateProduct(command.productName(), command.description(), command.skus().size());
        return locks.withLocks(List.of(SellerApplicationService.key("PRODUCT", command.productId())), () ->
                transactions.inTransaction(connection -> {
                    Shop shop = requireShop(connection, request.shopId());
                    Product existing = requireProduct(connection, command.productId(), shop.shopId());
                    String normalized = normalize(command.productName());
                    requireNameAvailable(connection, shop.shopId(), normalized, existing.productId());
                    command.skus().forEach(AdminProductService::validateSku);
                    Product updated = repository.updateProduct(connection, new Product(
                            existing.productId(), existing.shopId(), command.productName().strip(),
                            normalized, shop.category(), command.description().strip(),
                            ProductImageUrl.validate(command.coverImageUrl()), existing.status(),
                            existing.salesCount(), existing.rowVersion(), existing.createdAt(), clock.instant()),
                            command.expectedVersion());
                    List<ProductSku> stored = repository.findSkusByProduct(connection, existing.productId());
                    Set<String> retained = command.skus().stream().map(UpsertSkuCommand::skuId)
                            .filter(Objects::nonNull).collect(Collectors.toSet());
                    for (UpsertSkuCommand sku : command.skus()) {
                        if (sku.skuId() == null) {
                            repository.insertSku(connection, new ProductSku(UUID.randomUUID().toString(),
                                    existing.productId(), sku.skuName().strip(), sku.unitPrice(),
                                    sku.stockQuantity(), 0, sku.active(), 0));
                            continue;
                        }
                        ProductSku current = stored.stream().filter(value -> value.skuId().equals(sku.skuId()))
                                .findFirst().orElseThrow(() -> error(ShopErrorCode.SHOP_SKU_UNAVAILABLE,
                                        "SKU is not part of target product"));
                        if (sku.stockQuantity() < current.reservedQuantity())
                            throw error(ShopErrorCode.SHOP_SKU_UNAVAILABLE,
                                    "Stock cannot be below reserved quantity");
                        repository.updateSku(connection, new ProductSku(current.skuId(),
                                current.productId(), sku.skuName().strip(), sku.unitPrice(),
                                sku.stockQuantity(), current.reservedQuantity(), sku.active(),
                                current.rowVersion()), sku.expectedVersion());
                    }
                    for (ProductSku omitted : stored) {
                        if (retained.contains(omitted.skuId()) || !omitted.active()) continue;
                        if (omitted.reservedQuantity() > 0) throw error(ShopErrorCode.SHOP_SKU_UNAVAILABLE,
                                "Reserved SKU cannot be removed");
                        repository.updateSku(connection, new ProductSku(omitted.skuId(),
                                omitted.productId(), omitted.skuName(), omitted.unitPrice(),
                                omitted.stockQuantity(), omitted.reservedQuantity(), false,
                                omitted.rowVersion()), omitted.rowVersion());
                    }
                    log.productChanged(actor.userId(), shop.shopId(), existing.productId(), "UPDATED");
                    return toView(connection, updated);
                }));
    }

    public void changeStatus(String sessionToken, AdminChangeProductStatusCommand request) {
        var actor = users.requireAdministrator(sessionToken);
        Objects.requireNonNull(request, "request");
        ChangeProductStatusCommand command = Objects.requireNonNull(request.command(), "command");
        locks.withLocks(List.of(SellerApplicationService.key("PRODUCT", command.productId())), () ->
                transactions.inTransaction(connection -> {
                    Shop shop = requireShop(connection, request.shopId());
                    Product product = requireProduct(connection, command.productId(), shop.shopId());
                    if (command.targetStatus() == ProductStatus.ACTIVE && repository
                            .findSkusByProduct(connection, product.productId()).stream()
                            .noneMatch(sku -> sku.active() && sku.availableQuantity() > 0))
                        throw error(ShopErrorCode.SHOP_SKU_UNAVAILABLE,
                                "An active product requires a sellable SKU");
                    repository.updateProductStatus(connection, product.productId(), command.targetStatus(),
                            clock.instant(), command.expectedVersion());
                    log.productChanged(actor.userId(), shop.shopId(), product.productId(),
                            product.status() + "->" + command.targetStatus());
                    return null;
                }));
    }

    private Shop requireShop(Connection connection, String shopId) throws Exception {
        return repository.findShopById(connection, shopId).orElseThrow(() ->
                error(ShopErrorCode.SHOP_NOT_FOUND, "Target shop does not exist"));
    }

    private Product requireProduct(Connection connection, String productId, String shopId)
            throws Exception {
        Product product = repository.findProductById(connection, productId).orElseThrow(() ->
                error(ShopErrorCode.SHOP_PRODUCT_INACTIVE, "Product does not exist"));
        if (!product.shopId().equals(shopId)) throw error(ShopErrorCode.SHOP_NOT_OWNER,
                "Product does not belong to target shop");
        return product;
    }

    private void requireNameAvailable(Connection connection, String shopId, String normalized,
            String productId) throws Exception {
        var found = repository.findProductByNormalizedName(connection, shopId, normalized);
        if (found.isPresent() && !found.orElseThrow().productId().equals(productId))
            throw error(ShopErrorCode.SHOP_PRODUCT_NAME_EXISTS, "Product name already exists");
    }

    private ProductView toView(Connection connection, Product product) throws Exception {
        return new ProductView(product.productId(), product.productName(), product.category(),
                product.description(), product.coverImageUrl(), product.status(), product.salesCount(),
                product.rowVersion(), repository.findSkusByProduct(connection, product.productId())
                        .stream().map(ProductService::toSkuView).toList());
    }

    private static void validateProduct(String name, String description, int skuCount) {
        SellerApplicationService.requireText(name, "productName");
        SellerApplicationService.requireText(description, "description");
        if (skuCount == 0) throw new IllegalArgumentException("At least one SKU is required");
    }

    private static void validateSku(CreateSkuCommand sku) {
        validateSku(sku.skuName(), sku.unitPrice(), sku.stockQuantity());
    }

    private static void validateSku(UpsertSkuCommand sku) {
        validateSku(sku.skuName(), sku.unitPrice(), sku.stockQuantity());
    }

    private static void validateSku(String name, BigDecimal price, long stock) {
        SellerApplicationService.requireText(name, "skuName");
        if (price == null || price.signum() < 0 || stock < 0)
            throw new IllegalArgumentException("Invalid SKU price or stock");
    }

    private static String normalize(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private static edu.seu.vcampus.server.shop.ShopException error(ShopErrorCode code,
            String message) {
        return SellerApplicationService.error(code, message);
    }
}
