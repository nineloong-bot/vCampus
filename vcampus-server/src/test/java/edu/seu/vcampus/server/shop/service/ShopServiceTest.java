package edu.seu.vcampus.server.shop.service;

import edu.seu.vcampus.common.shop.ProductSearchQuery;
import edu.seu.vcampus.common.shop.ProductSortMode;
import edu.seu.vcampus.common.shop.ProductSummary;
import edu.seu.vcampus.common.shop.HomeProductQuery;
import edu.seu.vcampus.server.persistence.TransactionManager;
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

class ShopServiceTest {
    private ShopTestDatabase database;
    private TransactionManager transactions;
    private ShopService service;

    @BeforeEach
    void setUp() throws Exception {
        database = new ShopTestDatabase();
        transactions = new TransactionManager(database.connections());
        service = new ShopService(new AccessShopRepository(), transactions);
        seedCatalog();
    }

    @AfterEach
    void tearDown() throws Exception {
        database.close();
    }

    @Test
    void searchProductsExposesNormalizedSkuKeywordMatchingThroughTheServiceBoundary() {
        ProductSearchQuery query = new ProductSearchQuery("  轻量款  ", null, null, null,
                ProductSortMode.SALES_DESC, 0, 20);

        assertThat(service.searchProducts(query).items())
                .extracting(ProductSummary::productId)
                .containsExactly("service-product");
    }

    @Test
    void homeProductsAlwaysUseSalesDescendingEvenWhenAnotherSortIsRequested() {
        HomeProductQuery query = new HomeProductQuery(null, null, ProductSortMode.PRICE_DESC, 0, 20);

        assertThat(service.getHomeProducts(query).items())
                .extracting(ProductSummary::productId)
                .containsExactly("popular-product", "service-product");
    }

    @Test
    void rejectsCatalogPagesThatExceedTheShopPagingSafetyBounds() {
        assertThatThrownBy(() -> service.searchProducts(new ProductSearchQuery(
                null, null, null, null, ProductSortMode.SALES_DESC, 0, 101)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pageSize");
        assertThatThrownBy(() -> service.searchProducts(new ProductSearchQuery(
                null, null, null, null, ProductSortMode.SALES_DESC,
                Integer.MAX_VALUE, 20)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("page offset");
    }

    private void seedCatalog() {
        transactions.inTransaction(connection -> {
            Timestamp now = Timestamp.from(Instant.parse("2026-08-24T09:00:00Z"));
            try (var shop = connection.prepareStatement(
                    "INSERT INTO tblShop (shopId, ownerUserId, shopName, description, category, "
                            + "contact, shopStatus, rowVersion, createdAt, updatedAt) "
                            + "VALUES ('service-shop', 'owner-1', '服务测试店', '简介', '综合', "
                            + "'contact', 'ACTIVE', 0, ?, ?)")) {
                shop.setTimestamp(1, now);
                shop.setTimestamp(2, now);
                shop.executeUpdate();
            }
            try (var product = connection.prepareStatement(
                    "INSERT INTO tblProduct (productId, shopId, productName, normalizedProductName, category, description, "
                            + "productStatus, salesCount, rowVersion, createdAt, updatedAt) "
                            + "VALUES ('service-product', 'service-shop', '雨伞', '雨伞', '生活用品', "
                            + "'晴雨两用', 'ACTIVE', 1, 0, ?, ?)")) {
                product.setTimestamp(1, now);
                product.setTimestamp(2, now);
                product.executeUpdate();
            }
            try (var sku = connection.prepareStatement(
                    "INSERT INTO tblProductSku (skuId, productId, skuName, unitPrice, stockQuantity, "
                            + "reservedQuantity, isActive, rowVersion) "
                            + "VALUES ('service-sku', 'service-product', '轻量款', ?, 10, 0, TRUE, 0)")) {
                sku.setBigDecimal(1, new BigDecimal("19.00"));
                sku.executeUpdate();
            }
            try (var product = connection.prepareStatement(
                    "INSERT INTO tblProduct (productId, shopId, productName, normalizedProductName, category, description, "
                            + "productStatus, salesCount, rowVersion, createdAt, updatedAt) "
                            + "VALUES ('popular-product', 'service-shop', '畅销笔记本', '畅销笔记本', '文具', "
                            + "'销量更高但价格更低', 'ACTIVE', 10, 0, ?, ?)")) {
                product.setTimestamp(1, now);
                product.setTimestamp(2, now);
                product.executeUpdate();
            }
            try (var sku = connection.prepareStatement(
                    "INSERT INTO tblProductSku (skuId, productId, skuName, unitPrice, stockQuantity, "
                            + "reservedQuantity, isActive, rowVersion) "
                            + "VALUES ('popular-sku', 'popular-product', '普通装', ?, 10, 0, TRUE, 0)")) {
                sku.setBigDecimal(1, new BigDecimal("2.00"));
                sku.executeUpdate();
            }
            return null;
        });
    }
}
