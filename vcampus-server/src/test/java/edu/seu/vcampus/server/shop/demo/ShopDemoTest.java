package edu.seu.vcampus.server.shop.demo;

import edu.seu.vcampus.common.shop.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ShopDemoTest {
    @TempDir
    Path directory;

    @Test
    void catalogHasFiveReviewedCategoryGroupsAndMeaningfulSkuVariants() {
        var products = ShopDemoCatalog.products();

        assertThat(products).hasSize(100);
        assertThat(products.stream().collect(Collectors.groupingBy(
                ShopDemoCatalog.ProductSeed::category, Collectors.counting())))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "文具", 10L,
                        "图书", 30L,
                        "生活用品", 45L,
                        "药品", 5L,
                        "其他", 10L));
        assertThat(products).extracting(ShopDemoCatalog.ProductSeed::shopId)
                .containsOnly("demo-shop-stationery", "demo-shop-books", "demo-shop-daily",
                        "demo-shop-medicine", "demo-shop-other");
        assertThat(products).extracting(ShopDemoCatalog.ProductSeed::name)
                .doesNotHaveDuplicates();
        assertThat(products).allSatisfy(product -> {
            assertThat(product.skus()).isNotEmpty();
            assertThat(product.skus()).extracting(ShopDemoCatalog.SkuSeed::id)
                    .doesNotHaveDuplicates();
            assertThat(product.skus()).extracting(ShopDemoCatalog.SkuSeed::name)
                    .doesNotHaveDuplicates();
        });
        assertThat(products.stream().filter(product -> product.name().equals("中性笔"))
                .findFirst().orElseThrow().skus())
                .extracting(ShopDemoCatalog.SkuSeed::name)
                .containsExactly("黑色 0.5mm", "蓝色 0.5mm");
    }

    @Test
    void createsPersistentDatabaseAndCompletesCrossShopPayment() throws Exception {
        Path database = directory.resolve("vcampus-shop-demo.accdb");

        ShopDemoResult result = ShopDemo.run(database,
                Path.of("..", "vcampus-database", "schema"));

        assertThat(Files.isRegularFile(database)).isTrue();
        assertThat(result.catalogProductCount()).isEqualTo(100);
        assertThat(result.totalAmount()).isEqualByComparingTo(new BigDecimal("38.44"));
        assertThat(result.orderCount()).isEqualTo(2);
        assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        try (var connection = DriverManager.getConnection("jdbc:ucanaccess://" + database);
                var statement = connection.createStatement()) {
            assertThat(scalarLong(statement, "SELECT COUNT(*) FROM tblShop")).isEqualTo(5);
            assertThat(scalarLong(statement, "SELECT COUNT(*) FROM tblProduct")).isEqualTo(100);
            assertThat(scalarLong(statement, "SELECT COUNT(*) FROM tblOrder")).isEqualTo(2);
            assertThat(scalarLong(statement,
                    "SELECT SUM(reservedQuantity) FROM tblProductSku")).isZero();
            assertThat(scalarLong(statement,
                    "SELECT stockQuantity FROM tblProductSku "
                            + "WHERE skuId = 'demo-stationery-001-sku-1'"))
                    .isEqualTo(8);
            assertThat(scalarLong(statement,
                    "SELECT salesCount FROM tblProduct "
                            + "WHERE productId = 'demo-stationery-001'"))
                    .isEqualTo(502);
        }
    }

    private static long scalarLong(java.sql.Statement statement, String sql) throws Exception {
        try (var result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }
}
