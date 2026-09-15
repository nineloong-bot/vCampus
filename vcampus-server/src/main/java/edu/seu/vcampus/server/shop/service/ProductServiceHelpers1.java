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

/** Provides focused helper operations for {@link ProductService}. */
abstract class ProductServiceHelpers1 extends ProductServiceHelpers2 {
    protected ProductServiceHelpers1(ShopRepository repository, ShopUserPort users,
            TransactionManager transactions, ResourceLockManager locks, Clock clock) {
        super(repository, users, transactions, locks, clock);
    }


    protected Shop requireOwnedActiveShop(Connection connection, String ownerId) throws Exception {
        Shop shop = requireOwnedShop(connection, ownerId);
        if (shop.status() == ShopStatus.SUSPENDED) {
            throw SellerApplicationService.error(ShopErrorCode.SHOP_SUSPENDED, "Shop is suspended");
        }
        return shop;
    }

    protected Shop requireOwnedShop(Connection connection, String ownerId) throws Exception {
        return repository.findShopByOwner(connection, ownerId)
                .orElseThrow(() -> SellerApplicationService.error(
                        ShopErrorCode.SHOP_SELLER_NOT_APPROVED, "Approved shop required"));
    }

    protected Product requireOwnedProduct(Connection connection, String productId,
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

    protected ProductView toView(Connection connection, Product product) throws Exception {
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

    protected static ShopView toShopView(Shop shop) {
        return new ShopView(shop.shopId(), shop.ownerUserId(), shop.shopName(), shop.description(),
                shop.category(), shop.contact(), shop.status(), shop.suspensionReason(),
                shop.suspendedByUserId(), shop.suspendedAt(), shop.rowVersion());
    }

    protected static void validateProduct(String name, String category, String description) {
        requireText(name, "productName");
        requireText(category, "category");
        requireText(description, "description");
    }

    protected void requireProductNameAvailable(Connection connection, String shopId,
            String normalizedName, String productId) throws Exception {
        var existing = repository.findProductByNormalizedName(connection, shopId, normalizedName);
        if (existing.isPresent() && !existing.orElseThrow().productId().equals(productId)) {
            throw SellerApplicationService.error(ShopErrorCode.SHOP_PRODUCT_NAME_EXISTS,
                    "Product name already exists in this shop");
        }
    }

    protected static String normalizeProductName(String name) {
        return name.strip().toLowerCase(Locale.ROOT);
    }

    protected static String supportedCategory(String category) {
        try {
            return ShopCategories.requireSupported(category);
        } catch (IllegalArgumentException exception) {
            throw SellerApplicationService.error(ShopErrorCode.SHOP_CATEGORY_INVALID,
                    "Unsupported shop category");
        }
    }
}
