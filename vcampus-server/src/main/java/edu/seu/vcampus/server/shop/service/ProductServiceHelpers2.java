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
abstract class ProductServiceHelpers2 extends ProductServiceSupport {
    protected ProductServiceHelpers2(ShopRepository repository, ShopUserPort users,
            TransactionManager transactions, ResourceLockManager locks, Clock clock) {
        super(repository, users, transactions, locks, clock);
    }


    protected static void validateSku(CreateSkuCommand sku) {
        validateSku(sku.skuName(), sku.unitPrice(), sku.stockQuantity());
    }

    protected static void validateSku(String name, BigDecimal price, long stock) {
        requireText(name, "skuName");
        if (price == null || price.signum() < 0) {
            throw new IllegalArgumentException("unitPrice must be non-negative");
        }
        if (stock < 0) {
            throw new IllegalArgumentException("stockQuantity must be non-negative");
        }
    }

    protected static void requireText(String value, String name) {
        SellerApplicationService.requireText(value, name);
    }
}
