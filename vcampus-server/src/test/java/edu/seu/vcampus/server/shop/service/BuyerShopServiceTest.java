package edu.seu.vcampus.server.shop.service;

import edu.seu.vcampus.common.shop.ProductSearchQuery;
import edu.seu.vcampus.common.shop.ProductSortMode;
import edu.seu.vcampus.common.shop.ProductSummary;
import edu.seu.vcampus.common.shop.ShopErrorCode;
import edu.seu.vcampus.common.shop.ShopProductQuery;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.shop.ShopException;
import edu.seu.vcampus.server.shop.repository.AccessShopRepository;
import edu.seu.vcampus.server.shop.testutil.ShopTestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class BuyerShopServiceTest {
    private ShopTestDatabase database;
    private TransactionManager transactions;
    private ShopService service;

    @BeforeEach
    void setUp() throws Exception {
        database = new ShopTestDatabase();
        transactions = new TransactionManager(database.connections());
        service = new ShopService(new AccessShopRepository(), transactions);
        seedShop("shop-1", "owner-1", "文具店", "ACTIVE");
        seedShop("shop-2", "stranger-1", "书店", "ACTIVE");
    }

    @AfterEach
    void tearDown() throws Exception {
        database.close();
    }

    @Test
    void filtersByMinimumSellableSkuPriceAndSortsByPriceDescending() {
        seedProduct("p-low", "shop-1", "低价商品", 3, "2026-08-24T10:00:00Z");
        seedSku("sku-disabled", "p-low", "停用", "1.00", 10, 0, false);
        seedSku("sku-low", "p-low", "可售", "8.00", 1, 0, true);
        seedProduct("p-high", "shop-1", "高价商品", 1, "2026-08-24T11:00:00Z");
        seedSku("sku-high", "p-high", "标准", "12.00", 2, 0, true);
        seedSku("sku-higher", "p-high", "豪华", "20.00", 2, 0, true);
        seedProduct("p-empty", "shop-1", "无库存", 99, "2026-08-24T12:00:00Z");
        seedSku("sku-empty", "p-empty", "空", "10.00", 0, 0, true);

        ProductSearchQuery query = new ProductSearchQuery(null, null,
                new BigDecimal("8.00"), new BigDecimal("12.00"),
                ProductSortMode.PRICE_DESC, 0, 20);

        assertThat(service.searchProducts(query).items())
                .extracting(ProductSummary::productId, ProductSummary::minimumPrice)
                .containsExactly(tuple("p-high", new BigDecimal("12.00")),
                        tuple("p-low", new BigDecimal("8.00")));
    }

    @Test
    void defaultsToSalesDescendingAndBreaksTiesByNewestCreation() {
        seedProduct("p-old", "shop-1", "旧商品", 5, "2026-08-24T10:00:00Z");
        seedSku("sku-old", "p-old", "标准", "9.00", 2, 0, true);
        seedProduct("p-new", "shop-1", "新商品", 5, "2026-08-24T11:00:00Z");
        seedSku("sku-new", "p-new", "标准", "10.00", 2, 0, true);

        assertThat(service.searchProducts(new ProductSearchQuery(
                null, null, null, null, null, 0, 20)).items())
                .extracting(ProductSummary::productId).containsExactly("p-new", "p-old");
    }

    @Test
    void rejectsInvalidPriceRangeBeforeQuerying() {
        assertThatThrownBy(() -> service.searchProducts(new ProductSearchQuery(
                null, null, new BigDecimal("20.00"), new BigDecimal("10.00"),
                null, 0, 20)))
                .isInstanceOfSatisfying(ShopException.class, error -> assertThat(error.code())
                        .isEqualTo(ShopErrorCode.SHOP_PRICE_FILTER_INVALID));
    }

    @Test
    void shopPageRejectsMissingOrSuspendedShopAndNeverLeaksOtherProducts() {
        seedProduct("p-1", "shop-1", "店一商品", 1, "2026-08-24T10:00:00Z");
        seedSku("sku-1", "p-1", "标准", "9.00", 2, 0, true);
        seedProduct("p-2", "shop-2", "店二商品", 9, "2026-08-24T11:00:00Z");
        seedSku("sku-2", "p-2", "标准", "11.00", 2, 0, true);

        assertThatThrownBy(() -> service.getShop("missing-shop"))
                .isInstanceOfSatisfying(ShopException.class, error -> assertThat(error.code())
                        .isEqualTo(ShopErrorCode.SHOP_NOT_FOUND));

        ShopProductQuery query = new ShopProductQuery("shop-1", null, null,
                null, null, ProductSortMode.SALES_DESC, 0, 20);
        assertThat(service.getShopProducts(query).items())
                .extracting(ProductSummary::productId).containsExactly("p-1");

        transactions.inTransaction(connection -> {
            try (var statement = connection.prepareStatement(
                    "UPDATE tblShop SET shopStatus = 'SUSPENDED', suspensionReason = '维护', "
                            + "suspendedByUserId = 'admin-1', suspendedAt = ? WHERE shopId = 'shop-1'")) {
                statement.setTimestamp(1, Timestamp.from(Instant.parse("2026-08-28T08:00:00Z")));
                statement.executeUpdate();
            }
            return null;
        });
        assertThatThrownBy(() -> service.getShopProducts(query))
                .isInstanceOfSatisfying(ShopException.class, error -> assertThat(error.code())
                        .isEqualTo(ShopErrorCode.SHOP_SUSPENDED));
    }

    private void seedShop(String shopId, String ownerId, String name, String status) {
        transactions.inTransaction(connection -> {
            try (var statement = connection.prepareStatement(
                    "INSERT INTO tblShop (shopId, ownerUserId, shopName, description, category, "
                            + "contact, shopStatus, rowVersion, createdAt, updatedAt) "
                            + "VALUES (?, ?, ?, '简介', '综合', 'contact', ?, 0, ?, ?)")) {
                statement.setString(1, shopId);
                statement.setString(2, ownerId);
                statement.setString(3, name);
                statement.setString(4, status);
                Timestamp now = Timestamp.from(Instant.parse("2026-08-24T09:00:00Z"));
                statement.setTimestamp(5, now);
                statement.setTimestamp(6, now);
                statement.executeUpdate();
            }
            return null;
        });
    }

    private void seedProduct(String productId, String shopId, String name,
            long sales, String createdAt) {
        transactions.inTransaction(connection -> {
            try (var statement = connection.prepareStatement(
                    "INSERT INTO tblProduct (productId, shopId, productName, category, description, "
                            + "productStatus, salesCount, rowVersion, createdAt, updatedAt) "
                            + "VALUES (?, ?, ?, '文具', '详情', 'ACTIVE', ?, 0, ?, ?)")) {
                statement.setString(1, productId);
                statement.setString(2, shopId);
                statement.setString(3, name);
                statement.setLong(4, sales);
                Timestamp time = Timestamp.from(Instant.parse(createdAt));
                statement.setTimestamp(5, time);
                statement.setTimestamp(6, time);
                statement.executeUpdate();
            }
            return null;
        });
    }

    private void seedSku(String skuId, String productId, String name, String price,
            long stock, long reserved, boolean active) {
        transactions.inTransaction(connection -> {
            try (var statement = connection.prepareStatement(
                    "INSERT INTO tblProductSku (skuId, productId, skuName, unitPrice, stockQuantity, "
                            + "reservedQuantity, isActive, rowVersion) VALUES (?, ?, ?, ?, ?, ?, ?, 0)")) {
                statement.setString(1, skuId);
                statement.setString(2, productId);
                statement.setString(3, name);
                statement.setBigDecimal(4, new BigDecimal(price));
                statement.setLong(5, stock);
                statement.setLong(6, reserved);
                statement.setBoolean(7, active);
                statement.executeUpdate();
            }
            return null;
        });
    }
}
