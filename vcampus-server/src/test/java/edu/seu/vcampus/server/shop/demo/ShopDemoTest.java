package edu.seu.vcampus.server.shop.demo;

import edu.seu.vcampus.common.shop.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class ShopDemoTest {
    @TempDir
    Path directory;

    @Test
    void createsPersistentDatabaseAndCompletesCrossShopPayment() throws Exception {
        Path database = directory.resolve("vcampus-shop-demo.accdb");

        ShopDemoResult result = ShopDemo.run(database,
                Path.of("..", "vcampus-database", "schema"));

        assertThat(Files.isRegularFile(database)).isTrue();
        assertThat(result.totalAmount()).isEqualByComparingTo(new BigDecimal("16.00"));
        assertThat(result.orderCount()).isEqualTo(2);
        assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        try (var connection = DriverManager.getConnection("jdbc:ucanaccess://" + database);
                var statement = connection.createStatement()) {
            assertThat(scalarLong(statement, "SELECT COUNT(*) FROM tblShop")).isEqualTo(2);
            assertThat(scalarLong(statement, "SELECT COUNT(*) FROM tblOrder")).isEqualTo(2);
            assertThat(scalarLong(statement,
                    "SELECT SUM(reservedQuantity) FROM tblProductSku")).isZero();
            assertThat(scalarLong(statement,
                    "SELECT stockQuantity FROM tblProductSku WHERE skuId = 'demo-pen-black'"))
                    .isEqualTo(8);
            assertThat(scalarLong(statement,
                    "SELECT salesCount FROM tblProduct WHERE productId = 'demo-pen'"))
                    .isEqualTo(2);
        }
    }

    private static long scalarLong(java.sql.Statement statement, String sql) throws Exception {
        try (var result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }
}
