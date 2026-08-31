package edu.seu.vcampus.server.shop.payment;

import edu.seu.vcampus.common.shop.AddCartItemCommand;
import edu.seu.vcampus.common.shop.CheckoutCommand;
import edu.seu.vcampus.common.shop.CheckoutItem;
import edu.seu.vcampus.common.shop.CheckoutResult;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.shop.port.ShopUserKind;
import edu.seu.vcampus.server.shop.repository.AccessShopRepository;
import edu.seu.vcampus.server.shop.service.CartService;
import edu.seu.vcampus.server.shop.service.CheckoutService;
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

abstract class PaymentServiceTestSupport {
    protected static final Instant CHECKOUT_TIME = Instant.parse("2026-08-28T10:00:00Z");
    protected ShopTestDatabase database;
    protected FakeShopUserPort users;
    protected TransactionManager transactions;
    protected StripedResourceLockManager locks;
    protected SimulatedPaymentService payments;
    protected ReservationExpiryJob expiry;

    @BeforeEach
    void setUpPayment() throws Exception {
        database = new ShopTestDatabase();
        users = new FakeShopUserPort();
        users.add("buyer-token", "student-1", ShopUserKind.STUDENT, true);
        transactions = new TransactionManager(database.connections());
        locks = new StripedResourceLockManager();
        payments = new SimulatedPaymentService(users, transactions, locks,
                Clock.fixed(CHECKOUT_TIME.plusSeconds(60), ZoneOffset.UTC));
        expiry = new ReservationExpiryJob(transactions, locks,
                Clock.fixed(CHECKOUT_TIME.plusSeconds(16 * 60), ZoneOffset.UTC));
    }

    @AfterEach
    void tearDownPayment() throws Exception {
        database.close();
    }

    protected CheckoutResult seedCheckout(int quantity) {
        var repository = new AccessShopRepository();
        Clock clock = Clock.fixed(CHECKOUT_TIME, ZoneOffset.UTC);
        var carts = new CartService(repository, users, transactions, locks, clock);
        var checkout = new CheckoutService(repository, users, transactions, locks, clock);
        transactions.inTransaction(connection -> {
            Timestamp now = Timestamp.from(CHECKOUT_TIME.minusSeconds(3600));
            try (var shop = connection.prepareStatement(
                    "INSERT INTO tblShop (shopId, ownerUserId, shopName, description, category, "
                            + "contact, shopStatus, rowVersion, createdAt, updatedAt) "
                            + "VALUES ('shop-1', 'owner-1', '文具店', '简介', '综合', "
                            + "'contact', 'ACTIVE', 0, ?, ?)")) {
                shop.setTimestamp(1, now);
                shop.setTimestamp(2, now);
                shop.executeUpdate();
            }
            try (var product = connection.prepareStatement(
                    "INSERT INTO tblProduct (productId, shopId, productName, normalizedProductName, category, description, "
                            + "productStatus, salesCount, rowVersion, createdAt, updatedAt) "
                            + "VALUES ('product-1', 'shop-1', '签字笔', '签字笔', '综合', '详情', "
                            + "'ACTIVE', 0, 0, ?, ?)")) {
                product.setTimestamp(1, now);
                product.setTimestamp(2, now);
                product.executeUpdate();
            }
            connection.createStatement().executeUpdate(
                    "INSERT INTO tblProductSku (skuId, productId, skuName, unitPrice, "
                            + "stockQuantity, reservedQuantity, isActive, rowVersion) "
                            + "VALUES ('sku-1', 'product-1', '黑色', 2.50, 10, 0, TRUE, 0)");
            return null;
        });
        var cart = carts.addToCart("buyer-token", new AddCartItemCommand("sku-1", quantity));
        var item = cart.items().getFirst();
        return checkout.checkout("buyer-token", new CheckoutCommand(List.of(
                new CheckoutItem(item.cartItemId(), item.displayedUnitPrice())), false));
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

    protected String scalarString(String sql) {
        return transactions.inTransaction(connection -> {
            try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
                result.next();
                return result.getString(1);
            }
        });
    }
}
