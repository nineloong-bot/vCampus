package edu.seu.vcampus.server.shop.service;

import edu.seu.vcampus.common.shop.AddCartItemCommand;
import edu.seu.vcampus.common.shop.UpdateCartItemCommand;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.shop.port.ShopUserKind;
import edu.seu.vcampus.server.shop.repository.AccessShopRepository;
import edu.seu.vcampus.server.shop.testutil.FakeShopUserPort;
import edu.seu.vcampus.server.shop.testutil.ShopTestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CartServiceTest {
    private ShopTestDatabase database;
    private FakeShopUserPort users;
    private TransactionManager transactions;
    private CartService service;

    @BeforeEach
    void setUp() throws Exception {
        database = new ShopTestDatabase();
        users = new FakeShopUserPort();
        users.add("token-1", "student-1", ShopUserKind.STUDENT, true);
        users.add("token-2", "student-1", ShopUserKind.STUDENT, true);
        users.add("other-token", "teacher-1", ShopUserKind.TEACHER, true);
        transactions = new TransactionManager(database.connections());
        seedCatalog();
        service = new CartService(new AccessShopRepository(), users, transactions,
                new StripedResourceLockManager(), Clock.fixed(
                        Instant.parse("2026-08-28T09:00:00Z"), ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() throws Exception {
        database.close();
    }

    @Test
    void repeatedSkuAddsMergeAndSurviveNewSession() {
        service.addToCart("token-1", new AddCartItemCommand("sku-1", 2));
        service.addToCart("token-1", new AddCartItemCommand("sku-1", 3));

        CartService anotherSession = new CartService(new AccessShopRepository(), users,
                transactions, new StripedResourceLockManager(), Clock.systemUTC());
        var restored = anotherSession.getCart("token-2");

        assertThat(restored.items()).singleElement().satisfies(item -> {
            assertThat(item.quantity()).isEqualTo(5);
            assertThat(item.displayedUnitPrice()).isEqualByComparingTo("2.50");
        });
        assertThat(restored.displayedTotal()).isEqualByComparingTo("12.50");
    }

    @Test
    void updateRejectsStaleVersionAndAnotherUserCannotRemoveItem() {
        var added = service.addToCart("token-1", new AddCartItemCommand("sku-1", 1));
        var item = added.items().getFirst();
        service.updateCartItem("token-1", new UpdateCartItemCommand(
                item.cartItemId(), 2, item.rowVersion()));

        assertThatThrownBy(() -> service.updateCartItem("token-1",
                new UpdateCartItemCommand(item.cartItemId(), 3, item.rowVersion())))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.removeCartItem("other-token", item.cartItemId()))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void rejectsNonPositiveQuantityAndInactiveSku() {
        assertThatThrownBy(() -> service.addToCart("token-1",
                new AddCartItemCommand("sku-1", 0))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.addToCart("token-1",
                new AddCartItemCommand("sku-off", 1))).isInstanceOf(RuntimeException.class);
    }

    private void seedCatalog() {
        transactions.inTransaction(connection -> {
            Timestamp now = Timestamp.from(Instant.parse("2026-08-28T08:00:00Z"));
            try (var shop = connection.prepareStatement(
                    "INSERT INTO tblShop (shopId, ownerUserId, shopName, description, category, "
                            + "contact, shopStatus, rowVersion, createdAt, updatedAt) "
                            + "VALUES ('shop-1', 'owner-1', '文具店', '简介', '文具', 'contact', "
                            + "'ACTIVE', 0, ?, ?)")) {
                shop.setTimestamp(1, now);
                shop.setTimestamp(2, now);
                shop.executeUpdate();
            }
            try (var product = connection.prepareStatement(
                    "INSERT INTO tblProduct (productId, shopId, productName, normalizedProductName, category, description, "
                            + "productStatus, salesCount, rowVersion, createdAt, updatedAt) "
                            + "VALUES ('product-1', 'shop-1', '签字笔', '签字笔', '文具', '详情', 'ACTIVE', 0, 0, ?, ?)")) {
                product.setTimestamp(1, now);
                product.setTimestamp(2, now);
                product.executeUpdate();
            }
            insertSku(connection, "sku-1", true);
            insertSku(connection, "sku-off", false);
            return null;
        });
    }

    private static void insertSku(java.sql.Connection connection, String skuId,
            boolean active) throws Exception {
        try (var sku = connection.prepareStatement(
                "INSERT INTO tblProductSku (skuId, productId, skuName, unitPrice, stockQuantity, "
                        + "reservedQuantity, isActive, rowVersion) VALUES (?, 'product-1', '黑色', ?, 50, 0, ?, 0)")) {
            sku.setString(1, skuId);
            sku.setBigDecimal(2, new BigDecimal("2.50"));
            sku.setBoolean(3, active);
            sku.executeUpdate();
        }
    }
}
