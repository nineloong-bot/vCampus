package edu.seu.vcampus.server.shop.repository;

import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.shop.ProductSearchQuery;
import edu.seu.vcampus.common.shop.ProductSortMode;
import edu.seu.vcampus.common.shop.ProductSummary;
import edu.seu.vcampus.common.shop.ProductManagementQuery;
import edu.seu.vcampus.common.shop.ProductManagementSummary;
import edu.seu.vcampus.common.shop.ProductStatus;
import edu.seu.vcampus.common.shop.SellerOrderQuery;
import edu.seu.vcampus.common.shop.SellerOrderView;
import edu.seu.vcampus.common.shop.SellerApplicationStatus;
import edu.seu.vcampus.common.shop.ShopAdminQuery;
import edu.seu.vcampus.common.shop.ShopAdminSummary;
import edu.seu.vcampus.common.shop.ShopStatus;
import edu.seu.vcampus.server.shop.domain.SellerApplication;
import edu.seu.vcampus.server.shop.domain.Shop;
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

    @Test
    void applicationStatementSurvivesInsertAndUpdate() {
        SellerApplication inserted = transactions.inTransaction(connection -> repository.insertApplication(
                connection, new SellerApplication("application-1", "student-1", "校园文具店",
                        "服务师生", "文具", "13800000000", "初始经营计划",
                        SellerApplicationStatus.DRAFT, null, null, null, null, 0)));

        assertThat(inserted.applicationStatement()).isEqualTo("初始经营计划");

        SellerApplication updated = transactions.inTransaction(connection -> repository.updateApplication(
                connection, new SellerApplication(inserted.applicationId(), inserted.applicantUserId(),
                        inserted.shopName(), inserted.description(), inserted.category(), inserted.contact(),
                        "更新后的经营计划", inserted.status(), null, null, null, null,
                        inserted.rowVersion()), inserted.rowVersion()));

        assertThat(updated.applicationStatement()).isEqualTo("更新后的经营计划");
    }

    @Test
    void normalizedShopNameLookupAndAdministrativePagingAreStable() {
        insertShop(new Shop("shop-z", "student-1", "Campus Shop", "campus shop", "简介", "文具",
                "contact", ShopStatus.ACTIVE, null, null, null, 0, Instant.EPOCH, Instant.EPOCH));
        insertShop(new Shop("shop-b", "teacher-1", "同名店", "同名店-b", "简介", "图书",
                "contact", ShopStatus.ACTIVE, null, null, null, 0, Instant.EPOCH, Instant.EPOCH));
        insertShop(new Shop("shop-a", "owner-1", "同名店", "同名店-a", "简介", "生活用品",
                "contact", ShopStatus.ACTIVE, null, null, null, 0, Instant.EPOCH, Instant.EPOCH));

        Shop normalized = transactions.inTransaction(connection -> repository
                .findShopByNormalizedName(connection, "campus shop").orElseThrow());
        PageResult<ShopAdminSummary> page = transactions.inTransaction(connection -> repository
                .searchShops(connection, new ShopAdminQuery(null, ShopStatus.ACTIVE, 0, 10)));

        assertThat(normalized.shopId()).isEqualTo("shop-z");
        assertThat(page.items()).extracting(ShopAdminSummary::shopId)
                .containsExactly("shop-z", "shop-a", "shop-b");
    }

    @Test
    void managementProductsAggregateSkusAndSellerOrdersStayInsideTheSelectedShop() {
        seedShop("shop-owned", "owner-1", "卖家店铺");
        seedShop("shop-other", "teacher-1", "其他店铺");
        seedProduct("product-owned", "shop-owned", "聚合商品", "文具", "说明", 8,
                "2026-08-24T10:00:00Z");
        seedProduct("product-other", "shop-other", "其他商品", "文具", "说明", 2,
                "2026-08-24T11:00:00Z");
        seedSku("sku-owned-a", "product-owned", "A", "5.00", 10, 2);
        seedSku("sku-owned-b", "product-owned", "B", "2.50", 5, 1);
        seedSku("sku-other", "product-other", "其他", "9.00", 20, 0);
        seedPaidOrder("group-owned", "order-owned", "student-1", "shop-owned",
                "sku-owned-a", "历史商品名", "历史规格名", "2026-08-30T10:00:00Z");
        seedPaidOrder("group-other", "order-other", "stranger-1", "shop-other",
                "sku-other", "其他商品", "其他规格", "2026-08-30T11:00:00Z");

        PageResult<ProductManagementSummary> products = transactions.inTransaction(connection ->
                repository.searchManagedProducts(connection, new ProductManagementQuery(
                        "shop-owned", null, null, 0, 20)));
        java.util.List<SellerOrderView> orders = transactions.inTransaction(connection ->
                repository.findOrdersByShop(connection, "shop-owned",
                        new SellerOrderQuery(null, 0, 20)));

        assertThat(products.total()).isEqualTo(1);
        assertThat(products.items()).singleElement().satisfies(product -> {
            assertThat(product.productId()).isEqualTo("product-owned");
            assertThat(product.skuCount()).isEqualTo(2);
            assertThat(product.minimumPrice()).isEqualByComparingTo("2.50");
            assertThat(product.totalStock()).isEqualTo(15);
            assertThat(product.reservedStock()).isEqualTo(3);
            assertThat(product.salesCount()).isEqualTo(8);
        });
        assertThat(orders).singleElement().satisfies(order -> {
            assertThat(order.orderId()).isEqualTo("order-owned");
            assertThat(order.buyerUserId()).isEqualTo("student-1");
            assertThat(order.items()).singleElement().satisfies(item ->
                    assertThat(item.productName()).isEqualTo("历史商品名"));
        });
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

    private void insertShop(Shop shop) {
        transactions.inTransaction(connection -> repository.insertShop(connection, shop));
    }

    private void seedShop(String shopId, String ownerId, String name) {
        transactions.inTransaction(connection -> {
            try (var statement = connection.prepareStatement(
                    "INSERT INTO tblShop (shopId, ownerUserId, shopName, normalizedShopName, description, category, "
                            + "contact, shopStatus, rowVersion, createdAt, updatedAt) "
                            + "VALUES (?, ?, ?, ?, '简介', '综合', 'contact', 'ACTIVE', 0, ?, ?)")) {
                statement.setString(1, shopId);
                statement.setString(2, ownerId);
                statement.setString(3, name);
                statement.setString(4, name.strip().toLowerCase(java.util.Locale.ROOT));
                Timestamp now = Timestamp.from(Instant.parse("2026-08-24T09:00:00Z"));
                statement.setTimestamp(5, now);
                statement.setTimestamp(6, now);
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
        seedSku(skuId, productId, name, price, 10, 0);
    }

    private void seedSku(String skuId, String productId, String name, String price,
            long stock, long reserved) {
        transactions.inTransaction(connection -> {
            try (var statement = connection.prepareStatement(
                    "INSERT INTO tblProductSku (skuId, productId, skuName, unitPrice, stockQuantity, "
                            + "reservedQuantity, isActive, rowVersion) VALUES (?, ?, ?, ?, ?, ?, TRUE, 0)")) {
                statement.setString(1, skuId);
                statement.setString(2, productId);
                statement.setString(3, name);
                statement.setBigDecimal(4, new BigDecimal(price));
                statement.setLong(5, stock);
                statement.setLong(6, reserved);
                statement.executeUpdate();
            }
            return null;
        });
    }

    private void seedPaidOrder(String groupId, String orderId, String buyerId, String shopId,
            String skuId, String productName, String skuName, String paidAt) {
        transactions.inTransaction(connection -> {
            Timestamp paid = Timestamp.from(Instant.parse(paidAt));
            try (var group = connection.prepareStatement(
                    "INSERT INTO tblOrderGroup (orderGroupId, buyerUserId, totalAmount, groupStatus, "
                            + "createdAt, rowVersion) VALUES (?, ?, 6.00, 'PAID', ?, 0)")) {
                group.setString(1, groupId); group.setString(2, buyerId);
                group.setTimestamp(3, paid); group.executeUpdate();
            }
            try (var order = connection.prepareStatement(
                    "INSERT INTO tblOrder (orderId, orderGroupId, shopId, orderNumber, orderAmount, "
                            + "orderStatus, paidAt, createdAt, rowVersion) "
                            + "VALUES (?, ?, ?, ?, 6.00, 'PAID', ?, ?, 0)")) {
                order.setString(1, orderId); order.setString(2, groupId); order.setString(3, shopId);
                order.setString(4, "NO-" + orderId); order.setTimestamp(5, paid);
                order.setTimestamp(6, paid); order.executeUpdate();
            }
            try (var item = connection.prepareStatement(
                    "INSERT INTO tblOrderItem (orderItemId, orderId, skuId, productNameSnapshot, "
                            + "skuNameSnapshot, shopNameSnapshot, unitPrice, quantity, lineAmount) "
                            + "VALUES (?, ?, ?, ?, ?, '历史店铺名', 3.00, 2, 6.00)")) {
                item.setString(1, "item-" + orderId); item.setString(2, orderId);
                item.setString(3, skuId); item.setString(4, productName); item.setString(5, skuName);
                item.executeUpdate();
            }
            return null;
        });
    }
}
