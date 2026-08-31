package edu.seu.vcampus.server.shop.service;

import edu.seu.vcampus.common.shop.AddCartItemCommand;
import edu.seu.vcampus.common.shop.CartView;
import edu.seu.vcampus.common.shop.CheckoutCommand;
import edu.seu.vcampus.common.shop.CheckoutItem;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.shop.port.ShopUserKind;
import edu.seu.vcampus.server.shop.repository.AccessShopRepository;
import edu.seu.vcampus.server.shop.repository.ShopRepository;
import edu.seu.vcampus.server.shop.testutil.FakeShopUserPort;
import edu.seu.vcampus.server.shop.testutil.ShopTestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

abstract class CheckoutServiceTestSupport {
    private ShopTestDatabase database;
    protected FakeShopUserPort users;
    protected TransactionManager transactions;
    protected CartService carts;
    protected CheckoutService checkout;

    @BeforeEach
    void setUpCheckout() throws Exception {
        database = new ShopTestDatabase();
        users = new FakeShopUserPort();
        users.add("buyer-token", "student-1", ShopUserKind.STUDENT, true);
        transactions = new TransactionManager(database.connections());
        ShopRepository repository = new AccessShopRepository();
        var locks = new StripedResourceLockManager();
        Clock clock = Clock.fixed(Instant.parse("2026-08-28T10:00:00Z"), ZoneOffset.UTC);
        carts = new CartService(repository, users, transactions, locks, clock);
        checkout = new CheckoutService(repository, users, transactions, locks, clock);
    }

    @AfterEach
    void tearDownCheckout() throws Exception {
        database.close();
    }

    protected void addUser(String userId, String token) {
        transactions.inTransaction(connection -> {
            try (var statement = connection.prepareStatement(
                    "INSERT INTO tblUser (userId) VALUES (?)")) {
                statement.setString(1, userId);
                statement.executeUpdate();
            }
            return null;
        });
        users.add(token, userId, ShopUserKind.STUDENT, true);
    }

    protected void seedShop(String shopId, String ownerId, String shopName) {
        transactions.inTransaction(connection -> {
            Timestamp now = Timestamp.from(Instant.parse("2026-08-28T09:00:00Z"));
            try (var statement = connection.prepareStatement(
                    "INSERT INTO tblShop (shopId, ownerUserId, shopName, description, category, "
                            + "contact, shopStatus, rowVersion, createdAt, updatedAt) "
                            + "VALUES (?, ?, ?, '简介', '综合', 'contact', 'ACTIVE', 0, ?, ?)")) {
                statement.setString(1, shopId);
                statement.setString(2, ownerId);
                statement.setString(3, shopName);
                statement.setTimestamp(4, now);
                statement.setTimestamp(5, now);
                statement.executeUpdate();
            }
            return null;
        });
    }

    protected void seedProductAndSku(String productId, String productName, String shopId,
            String skuId, String skuName, String price, long stock) {
        transactions.inTransaction(connection -> {
            Timestamp now = Timestamp.from(Instant.parse("2026-08-28T09:00:00Z"));
            try (var product = connection.prepareStatement(
                    "INSERT INTO tblProduct (productId, shopId, productName, normalizedProductName, category, description, "
                            + "productStatus, salesCount, rowVersion, createdAt, updatedAt) "
                            + "VALUES (?, ?, ?, ?, '综合', '详情', 'ACTIVE', 0, 0, ?, ?)")) {
                product.setString(1, productId);
                product.setString(2, shopId);
                product.setString(3, productName);
                product.setString(4, productName.toLowerCase(java.util.Locale.ROOT));
                product.setTimestamp(5, now);
                product.setTimestamp(6, now);
                product.executeUpdate();
            }
            try (var sku = connection.prepareStatement(
                    "INSERT INTO tblProductSku (skuId, productId, skuName, unitPrice, stockQuantity, "
                            + "reservedQuantity, isActive, rowVersion) VALUES (?, ?, ?, ?, ?, 0, TRUE, 0)")) {
                sku.setString(1, skuId);
                sku.setString(2, productId);
                sku.setString(3, skuName);
                sku.setBigDecimal(4, new BigDecimal(price));
                sku.setLong(5, stock);
                sku.executeUpdate();
            }
            return null;
        });
    }

    protected CheckoutCommand checkoutCommand(String token, boolean acceptLatest) {
        CartView cart = carts.getCart(token);
        return new CheckoutCommand(cart.items().stream()
                .map(item -> new CheckoutItem(item.cartItemId(), item.displayedUnitPrice()))
                .toList(), acceptLatest);
    }

    protected long scalarLong(String sql) {
        return transactions.inTransaction(connection -> {
            try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
                result.next();
                return result.getLong(1);
            }
        });
    }

    protected BigDecimal scalarMoney(String sql) {
        return transactions.inTransaction(connection -> {
            try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
                result.next();
                return result.getBigDecimal(1);
            }
        });
    }
}
