package edu.seu.vcampus.server.shop.repository;

import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.shop.ProductSearchQuery;
import edu.seu.vcampus.common.shop.ProductSortMode;
import edu.seu.vcampus.common.shop.ProductSummary;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.shop.testutil.ShopTestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class AccessShopRepositoryTest {
    private ShopTestDatabase database;
    private TransactionManager transactions;
    private AccessShopRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        database = new ShopTestDatabase();
        transactions = new TransactionManager(database.connections());
        repository = new AccessShopRepository();
    }

    @AfterEach
    void tearDown() throws Exception {
        database.close();
    }

    @Test
    void unifiedKeywordMatchesEveryCatalogFieldAndCorrelatedSkuMatchKeepsOneProduct() {
        seedShop("shop-name", "owner-1", "星河文具铺");
        seedShop("shop-store", "stranger-1", "晨曦书屋");
        seedShop("shop-description", "student-1", "校园生活馆");
        seedShop("shop-sku", "teacher-1", "健康驿站");
        seedProduct("p-name", "shop-name", "流星签字笔", "文具", "顺滑书写", 4,
                "2026-08-24T10:00:00Z");
        seedProduct("p-store", "shop-store", "高等数学", "图书", "课程教材", 3,
                "2026-08-24T11:00:00Z");
        seedProduct("p-description", "shop-description", "柔软毛巾", "生活用品", "吸水速干面料", 2,
                "2026-08-24T12:00:00Z");
        seedProduct("p-sku", "shop-sku", "创可贴", "药品", "日常护理", 1,
                "2026-08-24T13:00:00Z");
        seedSku("sku-name", "p-name", "黑色", "5.00");
        seedSku("sku-store", "p-store", "平装", "30.00");
        seedSku("sku-description", "p-description", "标准装", "12.00");
        seedSku("sku-sku-cheap", "p-sku", "基础装", "2.00");
        seedSku("sku-sku-first", "p-sku", "午夜蓝小盒", "10.00");
        seedSku("sku-sku-second", "p-sku", "午夜蓝大盒", "12.00");

        assertIds(search("流星签字笔", null, null, null,
                ProductSortMode.SALES_DESC, 0, 20), "p-name");
        assertIds(search("晨曦书屋", null, null, null,
                ProductSortMode.SALES_DESC, 0, 20), "p-store");
        assertIds(search("文具", null, null, null,
                ProductSortMode.SALES_DESC, 0, 20), "p-name");
        assertIds(search("图书", null, null, null,
                ProductSortMode.SALES_DESC, 0, 20), "p-store");
        assertIds(search("生活用品", null, null, null,
                ProductSortMode.SALES_DESC, 0, 20), "p-description");
        assertIds(search("药品", null, null, null,
                ProductSortMode.SALES_DESC, 0, 20), "p-sku");
        assertIds(search("吸水速干", null, null, null,
                ProductSortMode.SALES_DESC, 0, 20), "p-description");

        PageResult<ProductSummary> skuMatch = search("  午夜蓝  ", null, null, null,
                ProductSortMode.SALES_DESC, 0, 20);
        assertThat(skuMatch.total()).isEqualTo(1);
        assertThat(skuMatch.items())
                .extracting(ProductSummary::productId, ProductSummary::minimumPrice)
                .containsExactly(tuple("p-sku", new BigDecimal("2.00")));
    }

    @Test
    void unifiedKeywordStillCombinesWithCategoryPriceSortAndPagination() {
        seedShop("shop-combined", "owner-1", "组合筛选店");
        seedProduct("p-low", "shop-combined", "低价组合商品", "文具", "组合关键词", 30,
                "2026-08-24T10:00:00Z");
        seedProduct("p-middle", "shop-combined", "中价组合商品", "文具", "组合关键词", 20,
                "2026-08-24T11:00:00Z");
        seedProduct("p-high", "shop-combined", "高价组合商品", "文具", "组合关键词", 10,
                "2026-08-24T12:00:00Z");
        seedProduct("p-other", "shop-combined", "异类组合商品", "图书", "组合关键词", 40,
                "2026-08-24T13:00:00Z");
        seedSku("sku-low", "p-low", "标准", "5.00");
        seedSku("sku-middle", "p-middle", "标准", "10.00");
        seedSku("sku-high", "p-high", "标准", "15.00");
        seedSku("sku-other", "p-other", "标准", "18.00");

        PageResult<ProductSummary> first = search("组合关键词", "文具",
                new BigDecimal("6.00"), new BigDecimal("16.00"),
                ProductSortMode.PRICE_DESC, 0, 1);
        PageResult<ProductSummary> second = search("组合关键词", "文具",
                new BigDecimal("6.00"), new BigDecimal("16.00"),
                ProductSortMode.PRICE_DESC, 1, 1);

        assertThat(first.total()).isEqualTo(2);
        assertIds(first, "p-high");
        assertThat(second.total()).isEqualTo(2);
        assertIds(second, "p-middle");
    }

    @Test
    void productIdIsTheFinalStableSortKeyAcrossCatalogPages() {
        seedShop("shop-ties", "owner-1", "稳定分页店");
        seedProduct("p-zeta", "shop-ties", "并列商品 Z", "文具", "稳定分页", 10,
                "2026-08-24T10:00:00Z");
        seedProduct("p-alpha", "shop-ties", "并列商品 A", "文具", "稳定分页", 10,
                "2026-08-24T10:00:00Z");
        seedProduct("p-middle", "shop-ties", "并列商品 M", "文具", "稳定分页", 10,
                "2026-08-24T10:00:00Z");
        seedSku("sku-zeta", "p-zeta", "标准", "10.00");
        seedSku("sku-alpha", "p-alpha", "标准", "10.00");
        seedSku("sku-middle", "p-middle", "标准", "10.00");

        assertIds(search("稳定分页", null, null, null,
                ProductSortMode.SALES_DESC, 0, 1), "p-alpha");
        assertIds(search("稳定分页", null, null, null,
                ProductSortMode.SALES_DESC, 1, 1), "p-middle");
        assertIds(search("稳定分页", null, null, null,
                ProductSortMode.SALES_DESC, 2, 1), "p-zeta");
        assertIds(search("稳定分页", null, null, null,
                ProductSortMode.PRICE_DESC, 0, 1), "p-alpha");
        assertIds(search("稳定分页", null, null, null,
                ProductSortMode.PRICE_DESC, 1, 1), "p-middle");
        assertIds(search("稳定分页", null, null, null,
                ProductSortMode.PRICE_DESC, 2, 1), "p-zeta");
    }

    private PageResult<ProductSummary> search(String keyword, String category,
            BigDecimal minPrice, BigDecimal maxPrice, ProductSortMode sortMode,
            int pageNumber, int pageSize) {
        ProductSearchQuery query = new ProductSearchQuery(keyword, category, minPrice, maxPrice,
                sortMode, pageNumber, pageSize);
        return transactions.inTransaction(connection -> repository.searchCatalog(connection, query, null));
    }

    private static void assertIds(PageResult<ProductSummary> page, String... ids) {
        assertThat(page.items()).extracting(ProductSummary::productId).containsExactly(ids);
    }

    private void seedShop(String shopId, String ownerId, String name) {
        transactions.inTransaction(connection -> {
            try (var statement = connection.prepareStatement(
                    "INSERT INTO tblShop (shopId, ownerUserId, shopName, description, category, "
                            + "contact, shopStatus, rowVersion, createdAt, updatedAt) "
                            + "VALUES (?, ?, ?, '简介', '综合', 'contact', 'ACTIVE', 0, ?, ?)")) {
                statement.setString(1, shopId);
                statement.setString(2, ownerId);
                statement.setString(3, name);
                Timestamp now = Timestamp.from(Instant.parse("2026-08-24T09:00:00Z"));
                statement.setTimestamp(4, now);
                statement.setTimestamp(5, now);
                statement.executeUpdate();
            }
            return null;
        });
    }

    private void seedProduct(String productId, String shopId, String name, String category,
            String description, long sales, String createdAt) {
        transactions.inTransaction(connection -> {
            try (var statement = connection.prepareStatement(
                    "INSERT INTO tblProduct (productId, shopId, productName, normalizedProductName, "
                            + "category, description, coverImageUrl, productStatus, salesCount, rowVersion, "
                            + "createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, ?, NULL, 'ACTIVE', ?, 0, ?, ?)")) {
                statement.setString(1, productId);
                statement.setString(2, shopId);
                statement.setString(3, name);
                statement.setString(4, name.toLowerCase(java.util.Locale.ROOT));
                statement.setString(5, category);
                statement.setString(6, description);
                statement.setLong(7, sales);
                Timestamp time = Timestamp.from(Instant.parse(createdAt));
                statement.setTimestamp(8, time);
                statement.setTimestamp(9, time);
                statement.executeUpdate();
            }
            return null;
        });
    }

    private void seedSku(String skuId, String productId, String name, String price) {
        transactions.inTransaction(connection -> {
            try (var statement = connection.prepareStatement(
                    "INSERT INTO tblProductSku (skuId, productId, skuName, unitPrice, stockQuantity, "
                            + "reservedQuantity, isActive, rowVersion) VALUES (?, ?, ?, ?, 10, 0, TRUE, 0)")) {
                statement.setString(1, skuId);
                statement.setString(2, productId);
                statement.setString(3, name);
                statement.setBigDecimal(4, new BigDecimal(price));
                statement.executeUpdate();
            }
            return null;
        });
    }
}
