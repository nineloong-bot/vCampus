package edu.seu.vcampus.server.shop.demo;

import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.MessageType;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.shop.ProductSearchQuery;
import edu.seu.vcampus.common.shop.ProductSortMode;
import edu.seu.vcampus.common.shop.ProductSummary;
import edu.seu.vcampus.common.user.LoginCommand;
import edu.seu.vcampus.common.user.LoginResult;
import edu.seu.vcampus.server.shop.repository.AccessShopRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ShopAuthDemoDatabaseTest {
    @TempDir
    Path temp;

    @Test
    void createsKnownBuyerAndOneHundredSellableProductsAcrossFourShops() throws Exception {
        Path database = temp.resolve("shop-auth.accdb");

        ShopAuthDemoDatabase.initialize(database, schemaDir(), seedDir());

        try (Connection connection = open(database)) {
            assertThat(count(connection,
                    "SELECT COUNT(*) FROM tblUser WHERE loginId='DEMO_BUYER' "
                            + "AND accountStatus='ACTIVE' AND mustChangePassword=FALSE"))
                    .isEqualTo(1);
            assertThat(count(connection,
                    "SELECT COUNT(*) FROM tblShop WHERE shopStatus='ACTIVE'"))
                    .isEqualTo(4);
            assertThat(count(connection,
                    "SELECT COUNT(*) FROM tblProduct")).isEqualTo(100);
            assertThat(count(connection,
                    "SELECT COUNT(*) FROM tblProduct WHERE shopId='demo-shop-stationery' "
                            + "AND category='文具'"))
                    .isEqualTo(10);
            assertThat(count(connection,
                    "SELECT COUNT(*) FROM tblProduct WHERE shopId='demo-shop-books' "
                            + "AND category='图书'"))
                    .isEqualTo(30);
            assertThat(count(connection,
                    "SELECT COUNT(*) FROM tblProduct WHERE shopId='demo-shop-daily' "
                            + "AND category='生活用品'"))
                    .isEqualTo(55);
            assertThat(count(connection,
                    "SELECT COUNT(*) FROM tblProduct WHERE shopId='demo-shop-medicine' "
                            + "AND category='药品'"))
                    .isEqualTo(5);

            List<String> productIds = strings(connection,
                    "SELECT productId FROM tblProduct");
            List<String> productNames = strings(connection,
                    "SELECT productName FROM tblProduct");
            List<String> descriptions = strings(connection,
                    "SELECT description FROM tblProduct");
            List<String> skuIds = strings(connection,
                    "SELECT skuId FROM tblProductSku");
            assertThat(new HashSet<>(productIds)).hasSize(100);
            assertThat(new HashSet<>(productNames)).hasSize(100);
            assertThat(new HashSet<>(skuIds)).hasSize(120);
            assertThat(productIds).allMatch(id -> id.matches(
                    "demo-(stationery|books|daily|medicine)-\\d{3}"));
            assertThat(descriptions).allMatch(description -> description.contains("分类：")
                    && description.contains("用途：") && description.contains("规格："));
            assertThat(skuCounts(connection)).allSatisfy((productId, skuCount) -> {
                int categoryIndex = Integer.parseInt(productId.substring(productId.length() - 3));
                assertThat(skuCount).isEqualTo(categoryIndex % 5 == 0 ? 2L : 1L);
            });
            assertThat(count(connection,
                    "SELECT COUNT(*) FROM tblProduct p WHERE NOT EXISTS "
                            + "(SELECT 1 FROM tblProductSku k WHERE k.productId=p.productId "
                            + "AND k.isActive=TRUE AND k.stockQuantity-k.reservedQuantity>0)"))
                    .isZero();
            assertThat(count(connection,
                    "SELECT COUNT(*) FROM tblProductSku WHERE skuName='组合装' "
                            + "AND isActive=TRUE AND stockQuantity>0"))
                    .isEqualTo(20);
            assertThat(count(connection,
                    "SELECT COUNT(*) FROM (SELECT salesCount FROM tblProduct "
                            + "GROUP BY salesCount) AS salesValues"))
                    .isGreaterThan(1);
            assertThat(count(connection,
                    "SELECT COUNT(*) FROM (SELECT unitPrice FROM tblProductSku "
                            + "GROUP BY unitPrice) AS priceValues"))
                    .isGreaterThan(1);
            assertThat(count(connection,
                    "SELECT COUNT(*) FROM (SELECT stockQuantity FROM tblProductSku "
                            + "GROUP BY stockQuantity) AS stockValues"))
                    .isGreaterThan(1);
        }
    }

    @Test
    void seedsDeterministicPaidAndPendingOrdersWithCanonicalCatalogAmounts() throws Exception {
        Path database = temp.resolve("orders.accdb");

        ShopAuthDemoDatabase.initialize(database, schemaDir(), seedDir());

        try (Connection connection = open(database)) {
            assertThat(count(connection,
                    "SELECT COUNT(*) FROM tblUser WHERE userId='demo-other-buyer' "
                            + "AND loginId='DEMO_OTHER_BUYER' AND accountStatus='ACTIVE'"))
                    .isEqualTo(1);
            assertThat(count(connection,
                    "SELECT COUNT(*) FROM tblOrderGroup WHERE buyerUserId='demo-buyer' "
                            + "AND groupStatus='PAID'"))
                    .isEqualTo(2);
            assertThat(count(connection,
                    "SELECT COUNT(*) FROM tblOrderGroup WHERE buyerUserId='demo-buyer' "
                            + "AND groupStatus='PENDING_PAYMENT'"))
                    .isEqualTo(1);
            assertThat(count(connection,
                    "SELECT COUNT(*) FROM tblOrderGroup WHERE buyerUserId='demo-other-buyer' "
                            + "AND groupStatus='PAID'"))
                    .isEqualTo(1);
            assertThat(strings(connection,
                    "SELECT orderId FROM tblOrder WHERE orderStatus='PAID' "
                            + "ORDER BY paidAt DESC, orderId"))
                    .containsExactly("demo-order-buyer-paid-new",
                            "demo-order-other-paid", "demo-order-buyer-paid-old");
            assertThat(instant(connection,
                    "SELECT paidAt FROM tblOrder WHERE orderId=?",
                    "demo-order-buyer-paid-new"))
                    .isEqualTo(Instant.parse("2026-08-29T09:05:00Z"));
            assertThat(instant(connection,
                    "SELECT paidAt FROM tblOrder WHERE orderId=?",
                    "demo-order-buyer-paid-old"))
                    .isEqualTo(Instant.parse("2026-08-25T08:05:00Z"));
            assertThat(money(connection,
                    "SELECT totalAmount FROM tblOrderGroup WHERE orderGroupId=?",
                    "demo-group-buyer-paid-new"))
                    .isEqualByComparingTo("20.46");
            assertThat(money(connection,
                    "SELECT totalAmount FROM tblOrderGroup WHERE orderGroupId=?",
                    "demo-group-buyer-paid-old"))
                    .isEqualByComparingTo("6.70");
            assertThat(money(connection,
                    "SELECT totalAmount FROM tblOrderGroup WHERE orderGroupId=?",
                    "demo-group-other-paid"))
                    .isEqualByComparingTo("32.70");
            assertThat(money(connection,
                    "SELECT totalAmount FROM tblOrderGroup WHERE orderGroupId=?",
                    "demo-group-buyer-pending"))
                    .isEqualByComparingTo("7.61");
            assertThat(count(connection,
                    "SELECT COUNT(*) FROM (((tblOrderGroup g INNER JOIN tblOrder o "
                            + "ON g.orderGroupId=o.orderGroupId) INNER JOIN tblOrderItem i "
                            + "ON o.orderId=i.orderId) INNER JOIN tblProductSku k "
                            + "ON i.skuId=k.skuId) INNER JOIN tblProduct p "
                            + "ON k.productId=p.productId WHERE i.productNameSnapshot=p.productName "
                            + "AND i.skuNameSnapshot=k.skuName AND i.unitPrice=k.unitPrice "
                            + "AND i.lineAmount=i.unitPrice*i.quantity "
                            + "AND o.orderAmount=i.lineAmount AND g.totalAmount=o.orderAmount"))
                    .isEqualTo(4);
            assertThat(count(connection,
                    "SELECT COUNT(*) FROM (tblPayment p INNER JOIN tblOrderGroup g "
                            + "ON p.orderGroupId=g.orderGroupId) INNER JOIN tblOrder o "
                            + "ON g.orderGroupId=o.orderGroupId WHERE g.groupStatus='PAID' "
                            + "AND o.orderStatus='PAID' AND o.paidAt IS NOT NULL "
                            + "AND p.paymentStatus='SUCCEEDED' AND p.completedAt IS NOT NULL "
                            + "AND p.amount=g.totalAmount AND p.amount=o.orderAmount"))
                    .isEqualTo(3);
            assertThat(count(connection,
                    "SELECT COUNT(*) FROM tblPaymentAttempt WHERE attemptStatus='SUCCEEDED' "
                            + "AND completedAt IS NOT NULL"))
                    .isEqualTo(3);
            assertThat(count(connection,
                    "SELECT COUNT(*) FROM (tblPayment p INNER JOIN tblOrderGroup g "
                            + "ON p.orderGroupId=g.orderGroupId) INNER JOIN tblOrder o "
                            + "ON g.orderGroupId=o.orderGroupId "
                            + "WHERE g.orderGroupId='demo-group-buyer-pending' "
                            + "AND g.groupStatus='PENDING_PAYMENT' "
                            + "AND o.orderStatus='PENDING_PAYMENT' AND o.paidAt IS NULL "
                            + "AND p.paymentStatus='PENDING' AND p.completedAt IS NULL "
                            + "AND p.amount=g.totalAmount AND p.amount=o.orderAmount"))
                    .isEqualTo(1);
        }
    }

    @Test
    void supportsSortedCatalogPagesCategoryPagesAndDeduplicatedSkuSearch() throws Exception {
        Path database = temp.resolve("catalog.accdb");
        ShopAuthDemoDatabase.initialize(database, schemaDir(), seedDir());

        try (Connection connection = open(database)) {
            AccessShopRepository repository = new AccessShopRepository();
            List<ProductSummary> firstForty = new ArrayList<>();
            for (int page = 0; page < 2; page++) {
                var result = repository.searchCatalog(connection,
                        query(null, null, page, 20), null);
                assertThat(result.total()).isEqualTo(100);
                assertThat(result.items()).hasSize(20);
                firstForty.addAll(result.items());
            }
            assertThat(firstForty).isSortedAccordingTo(
                    (left, right) -> Long.compare(right.salesCount(), left.salesCount()));

            var booksFirstPage = repository.searchCatalog(connection,
                    query(null, "图书", 0, 20), null);
            var booksSecondPage = repository.searchCatalog(connection,
                    query(null, "图书", 1, 20), null);
            assertThat(booksFirstPage.total()).isEqualTo(30);
            assertThat(booksFirstPage.items()).hasSize(20);
            assertThat(booksSecondPage.items()).hasSize(10);

            var dailyThirdPage = repository.searchCatalog(connection,
                    query(null, "生活用品", 2, 20), null);
            assertThat(dailyThirdPage.total()).isEqualTo(55);
            assertThat(dailyThirdPage.items()).hasSize(15);

            var skuMatches = repository.searchCatalog(connection,
                    query("组合装", null, 0, 100), null);
            assertThat(skuMatches.total()).isEqualTo(20);
            assertThat(skuMatches.items()).hasSize(20);
            assertThat(skuMatches.items().stream().map(ProductSummary::productId))
                    .doesNotHaveDuplicates();
        }
    }

    @Test
    void replacesPriorDemoStateWhenInitializedAgain() throws Exception {
        Path database = temp.resolve("repeatable.accdb");
        ShopAuthDemoDatabase.initialize(database, schemaDir(), seedDir());
        try (Connection connection = open(database);
                var statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE tblProductSku SET stockQuantity=2 "
                    + "WHERE skuId='demo-stationery-001-sku-1'");
        }

        ShopAuthDemoDatabase.initialize(database, schemaDir(), seedDir());

        try (Connection connection = open(database)) {
            assertThat(count(connection, "SELECT COUNT(*) FROM tblUser WHERE userId='demo-buyer'"))
                    .isEqualTo(1);
            assertThat(count(connection, "SELECT COUNT(*) FROM tblShop"))
                    .isEqualTo(4);
            assertThat(count(connection,
                    "SELECT COUNT(*) FROM tblProductSku "
                            + "WHERE skuId='demo-stationery-001-sku-1' "
                            + "AND stockQuantity=10"))
                    .isEqualTo(1);
        }
    }

    @Test
    void runtimeUsesPreparedDatabaseAndServesLoginOnEphemeralPort() throws Exception {
        Path database = temp.resolve("runtime.accdb");
        ShopAuthDemoDatabase.initialize(database, schemaDir(), seedDir());
        try (Connection connection = open(database);
                var statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE tblProductSku SET stockQuantity=7 "
                    + "WHERE skuId='demo-stationery-001-sku-1'");
        }

        try (ShopAuthDemoRuntime runtime = ShopAuthDemoRuntime.start(database, 0)) {
            assertThat(runtime.localPort()).isPositive();
            assertThat(login(runtime.localPort()).user().loginId()).isEqualTo("DEMO_BUYER");
        }

        try (Connection connection = open(database)) {
            assertThat(count(connection,
                    "SELECT COUNT(*) FROM tblProductSku "
                            + "WHERE skuId='demo-stationery-001-sku-1' "
                            + "AND stockQuantity=7"))
                    .isEqualTo(1);
        }
    }

    private static LoginResult login(int port) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(5_000);
            ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
            output.flush();
            try (output; ObjectInputStream input = new ObjectInputStream(socket.getInputStream())) {
                Message request = new Message(UUID.randomUUID().toString(), MessageType.REQUEST,
                        "USER_LOGIN", null,
                        new LoginCommand("DEMO_BUYER", "DemoPassword7".toCharArray(), "demo-test"),
                        System.currentTimeMillis());
                output.writeObject(request);
                output.flush();
                Message response = (Message) input.readObject();
                assertThat(response.body()).isInstanceOf(ResponseBody.class);
                ResponseBody<?> body = (ResponseBody<?>) response.body();
                assertThat(body.success()).isTrue();
                assertThat(body.data()).isInstanceOf(LoginResult.class);
                return (LoginResult) body.data();
            }
        }
    }

    private static Connection open(Path database) throws Exception {
        return DriverManager.getConnection("jdbc:ucanaccess://" + database
                + ";immediatelyReleaseResources=true");
    }

    private static long count(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement();
                var result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }

    private static List<String> strings(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement();
                var result = statement.executeQuery(sql)) {
            List<String> values = new ArrayList<>();
            while (result.next()) {
                values.add(result.getString(1));
            }
            return values;
        }
    }

    private static Instant instant(Connection connection, String sql, String parameter)
            throws Exception {
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, parameter);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                Timestamp timestamp = result.getTimestamp(1);
                assertThat(timestamp).isNotNull();
                return timestamp.toInstant();
            }
        }
    }

    private static BigDecimal money(Connection connection, String sql, String parameter)
            throws Exception {
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, parameter);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getBigDecimal(1);
            }
        }
    }

    private static Map<String, Long> skuCounts(Connection connection) throws Exception {
        try (var statement = connection.createStatement();
                var result = statement.executeQuery(
                        "SELECT productId, COUNT(*) FROM tblProductSku GROUP BY productId")) {
            Map<String, Long> counts = new LinkedHashMap<>();
            while (result.next()) {
                counts.put(result.getString(1), result.getLong(2));
            }
            return counts;
        }
    }

    private static ProductSearchQuery query(
            String keyword, String category, int page, int pageSize) {
        return new ProductSearchQuery(keyword, category, null, null,
                ProductSortMode.SALES_DESC, page, pageSize);
    }

    private static Path schemaDir() {
        return projectDirectory("schema");
    }

    private static Path seedDir() {
        return projectDirectory("seed");
    }

    private static Path projectDirectory(String name) {
        Path fromModule = Path.of("..", "vcampus-database", name);
        return Files.isDirectory(fromModule) ? fromModule : Path.of("vcampus-database", name);
    }
}
