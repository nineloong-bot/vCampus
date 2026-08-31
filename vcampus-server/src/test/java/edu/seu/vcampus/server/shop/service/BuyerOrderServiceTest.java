package edu.seu.vcampus.server.shop.service;

import edu.seu.vcampus.common.shop.OrderStatus;
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

class BuyerOrderServiceTest {
    private ShopTestDatabase database;
    private TransactionManager transactions;
    private BuyerOrderService service;

    @BeforeEach
    void setUp() throws Exception {
        database = new ShopTestDatabase();
        transactions = new TransactionManager(database.connections());
        service = new BuyerOrderService(new AccessShopRepository(), transactions);
        seedCatalog();
    }

    @AfterEach
    void tearDown() throws Exception {
        database.close();
    }

    @Test
    void returnsOnlyBuyersFullyPaidOrdersNewestFirstWithStableTieAndCompleteItems() {
        Instant early = Instant.parse("2026-08-30T08:00:00Z");
        Instant late = Instant.parse("2026-08-30T09:00:00Z");
        seedOrder("group-zeta", "student-1", "PAID", "order-zeta", "O-ZETA",
                "shop-1", "7.50", "PAID", early);
        seedItem("item-zeta", "order-zeta", "sku-1", "签字笔快照", "黑色快照",
                "文具店快照", "2.50", 3, "7.50");
        seedOrder("group-late", "student-1", "PAID", "order-late", "O-LATE",
                "shop-2", "12.00", "PAID", late);
        seedItem("item-late-1", "order-late", "sku-2", "笔记本快照", "A5快照",
                "书店快照", "5.00", 2, "10.00");
        seedItem("item-late-2", "order-late", "sku-3", "书签快照", "纸质快照",
                "书店快照", "2.00", 1, "2.00");
        seedOrder("group-alpha", "student-1", "PAID", "order-alpha", "O-ALPHA",
                "shop-1", "2.50", "PAID", early);
        seedItem("item-alpha", "order-alpha", "sku-1", "签字笔快照", "黑色快照",
                "文具店快照", "2.50", 1, "2.50");

        seedOrder("group-other", "other-1", "PAID", "order-other", "O-OTHER",
                "shop-1", "2.50", "PAID", late.plusSeconds(60));
        seedItem("item-other", "order-other", "sku-1", "他人商品", "他人规格",
                "文具店快照", "2.50", 1, "2.50");
        seedOrder("group-pending", "student-1", "PENDING_PAYMENT", "order-pending",
                "O-PENDING", "shop-1", "2.50", "PENDING_PAYMENT", null);
        seedItem("item-pending", "order-pending", "sku-1", "待支付商品", "待支付规格",
                "文具店快照", "2.50", 1, "2.50");
        seedOrder("group-no-paid-at", "student-1", "PAID", "order-no-paid-at",
                "O-NO-PAID-AT", "shop-1", "2.50", "PAID", null);
        seedItem("item-no-paid-at", "order-no-paid-at", "sku-1", "异常商品", "异常规格",
                "文具店快照", "2.50", 1, "2.50");
        seedOrder("group-paid-order-pending", "student-1", "PAID",
                "order-status-pending", "O-ORDER-PENDING", "shop-1", "2.50",
                "PENDING_PAYMENT", late.plusSeconds(120));
        seedItem("item-status-pending", "order-status-pending", "sku-1",
                "订单待支付商品", "订单待支付规格", "文具店快照", "2.50", 1, "2.50");
        seedOrder("group-status-pending", "student-1", "PENDING_PAYMENT",
                "order-paid", "O-GROUP-PENDING", "shop-1", "2.50",
                "PAID", late.plusSeconds(180));
        seedItem("item-group-pending", "order-paid", "sku-1",
                "订单组待支付商品", "订单组待支付规格", "文具店快照", "2.50", 1, "2.50");

        var history = service.getPaidOrders("student-1");

        assertThat(history.orders()).extracting(order -> order.orderId())
                .containsExactly("order-late", "order-alpha", "order-zeta");
        var newest = history.orders().getFirst();
        assertThat(newest.orderNumber()).isEqualTo("O-LATE");
        assertThat(newest.shopId()).isEqualTo("shop-2");
        assertThat(newest.shopName()).isEqualTo("书店");
        assertThat(newest.totalAmount()).isEqualByComparingTo("12.00");
        assertThat(newest.paidAt()).isEqualTo(late);
        assertThat(newest.status()).isEqualTo(OrderStatus.PAID);
        assertThat(newest.items()).hasSize(2);
        assertThat(newest.items().getFirst().productId()).isEqualTo("product-2");
        assertThat(newest.items().getFirst().productName()).isEqualTo("笔记本快照");
        assertThat(newest.items().getFirst().skuId()).isEqualTo("sku-2");
        assertThat(newest.items().getFirst().skuName()).isEqualTo("A5快照");
        assertThat(newest.items().getFirst().quantity()).isEqualTo(2);
        assertThat(newest.items().getFirst().unitPrice()).isEqualByComparingTo("5.00");
        assertThat(newest.items().getFirst().lineAmount()).isEqualByComparingTo("10.00");
        assertThat(newest.items().get(1).productId()).isEqualTo("product-3");
    }

    @Test
    void rejectsEntirePaidHistoryWhenOrderItemReferencesMissingSku() {
        Instant paidAt = Instant.parse("2026-08-30T09:00:00Z");
        seedOrder("group-orphan", "student-1", "PAID", "order-orphan", "O-ORPHAN",
                "shop-1", "5.00", "PAID", paidAt);
        seedItem("item-valid", "order-orphan", "sku-1", "签字笔快照", "黑色快照",
                "文具店快照", "2.50", 1, "2.50");
        seedItem("item-orphan", "order-orphan", "missing-sku", "历史商品快照", "历史规格快照",
                "文具店快照", "2.50", 1, "2.50");

        assertThatThrownBy(() -> service.getPaidOrders("student-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing-sku");
    }

    private void seedCatalog() {
        transactions.inTransaction(connection -> {
            Timestamp created = Timestamp.from(Instant.parse("2026-08-29T00:00:00Z"));
            try (var shop = connection.prepareStatement(
                    "INSERT INTO tblShop (shopId, ownerUserId, shopName, description, category, "
                            + "contact, shopStatus, rowVersion, createdAt, updatedAt) "
                            + "VALUES (?, ?, ?, '简介', '综合', 'contact', 'ACTIVE', 0, ?, ?)")) {
                insertShop(shop, "shop-1", "owner-1", "文具店", created);
                insertShop(shop, "shop-2", "stranger-1", "书店", created);
            }
            try (var product = connection.prepareStatement(
                    "INSERT INTO tblProduct (productId, shopId, productName, category, description, "
                            + "productStatus, salesCount, rowVersion, createdAt, updatedAt) "
                            + "VALUES (?, ?, ?, '综合', '详情', 'ACTIVE', 0, 0, ?, ?)")) {
                insertProduct(product, "product-1", "shop-1", "签字笔", created);
                insertProduct(product, "product-2", "shop-2", "笔记本", created);
                insertProduct(product, "product-3", "shop-2", "书签", created);
            }
            try (var sku = connection.prepareStatement(
                    "INSERT INTO tblProductSku (skuId, productId, skuName, unitPrice, "
                            + "stockQuantity, reservedQuantity, isActive, rowVersion) "
                            + "VALUES (?, ?, ?, ?, 10, 0, TRUE, 0)")) {
                insertSku(sku, "sku-1", "product-1", "黑色", "2.50");
                insertSku(sku, "sku-2", "product-2", "A5", "5.00");
                insertSku(sku, "sku-3", "product-3", "纸质", "2.00");
            }
            return null;
        });
    }

    private void seedOrder(String groupId, String buyerId, String groupStatus,
            String orderId, String orderNumber, String shopId, String amount,
            String orderStatus, Instant paidAt) {
        transactions.inTransaction(connection -> {
            Timestamp created = Timestamp.from(Instant.parse("2026-08-29T00:00:00Z"));
            try (var group = connection.prepareStatement(
                    "INSERT INTO tblOrderGroup (orderGroupId, buyerUserId, totalAmount, "
                            + "groupStatus, createdAt, rowVersion) VALUES (?, ?, ?, ?, ?, 0)")) {
                group.setString(1, groupId);
                group.setString(2, buyerId);
                group.setBigDecimal(3, new BigDecimal(amount));
                group.setString(4, groupStatus);
                group.setTimestamp(5, created);
                group.executeUpdate();
            }
            try (var order = connection.prepareStatement(
                    "INSERT INTO tblOrder (orderId, orderGroupId, shopId, orderNumber, "
                            + "orderAmount, orderStatus, createdAt, paidAt, rowVersion) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)")) {
                order.setString(1, orderId);
                order.setString(2, groupId);
                order.setString(3, shopId);
                order.setString(4, orderNumber);
                order.setBigDecimal(5, new BigDecimal(amount));
                order.setString(6, orderStatus);
                order.setTimestamp(7, created);
                order.setTimestamp(8, paidAt == null ? null : Timestamp.from(paidAt));
                order.executeUpdate();
            }
            return null;
        });
    }

    private void seedItem(String itemId, String orderId, String skuId,
            String productName, String skuName, String shopName, String unitPrice,
            int quantity, String lineAmount) {
        transactions.inTransaction(connection -> {
            try (var item = connection.prepareStatement(
                    "INSERT INTO tblOrderItem (orderItemId, orderId, skuId, productNameSnapshot, "
                            + "skuNameSnapshot, shopNameSnapshot, unitPrice, quantity, lineAmount) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                item.setString(1, itemId);
                item.setString(2, orderId);
                item.setString(3, skuId);
                item.setString(4, productName);
                item.setString(5, skuName);
                item.setString(6, shopName);
                item.setBigDecimal(7, new BigDecimal(unitPrice));
                item.setInt(8, quantity);
                item.setBigDecimal(9, new BigDecimal(lineAmount));
                item.executeUpdate();
            }
            return null;
        });
    }

    private static void insertShop(java.sql.PreparedStatement statement, String shopId,
            String ownerId, String name, Timestamp created) throws Exception {
        statement.setString(1, shopId);
        statement.setString(2, ownerId);
        statement.setString(3, name);
        statement.setTimestamp(4, created);
        statement.setTimestamp(5, created);
        statement.executeUpdate();
    }

    private static void insertProduct(java.sql.PreparedStatement statement, String productId,
            String shopId, String name, Timestamp created) throws Exception {
        statement.setString(1, productId);
        statement.setString(2, shopId);
        statement.setString(3, name);
        statement.setTimestamp(4, created);
        statement.setTimestamp(5, created);
        statement.executeUpdate();
    }

    private static void insertSku(java.sql.PreparedStatement statement, String skuId,
            String productId, String name, String price) throws Exception {
        statement.setString(1, skuId);
        statement.setString(2, productId);
        statement.setString(3, name);
        statement.setBigDecimal(4, new BigDecimal(price));
        statement.executeUpdate();
    }
}
