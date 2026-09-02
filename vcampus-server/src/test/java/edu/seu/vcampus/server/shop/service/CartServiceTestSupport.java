package edu.seu.vcampus.server.shop.service;

import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.shop.port.ShopUserKind;
import edu.seu.vcampus.server.shop.repository.AccessShopRepository;
import edu.seu.vcampus.server.shop.testutil.FakeShopUserPort;
import edu.seu.vcampus.server.shop.testutil.ShopTestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;

abstract class CartServiceTestSupport {
    private ShopTestDatabase database;
    private CartService service;

    @BeforeEach
    void setUpCart() throws Exception {
        database = new ShopTestDatabase();
        var users = new FakeShopUserPort();
        users.add("token-1", "student-1", ShopUserKind.STUDENT, true);
        var transactions = new TransactionManager(database.connections());
        Timestamp now = Timestamp.from(Instant.parse("2026-08-28T08:00:00Z"));
        transactions.inTransaction(connection -> {
            try (var shop = connection.prepareStatement(
                    "INSERT INTO tblShop (shopId, ownerUserId, shopName, normalizedShopName, description, category, "
                            + "contact, shopStatus, rowVersion, createdAt, updatedAt) "
                            + "VALUES ('shop-1', 'owner-1', '文具店', '文具店', '简介', '文具', 'contact', "
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
            try (var sku = connection.prepareStatement(
                    "INSERT INTO tblProductSku (skuId, productId, skuName, unitPrice, stockQuantity, "
                            + "reservedQuantity, isActive, rowVersion) VALUES "
                            + "('sku-1', 'product-1', '黑色', ?, 50, 0, TRUE, 0)")) {
                sku.setBigDecimal(1, new BigDecimal("2.50"));
                sku.executeUpdate();
            }
            return null;
        });
        service = new CartService(new AccessShopRepository(), users, transactions,
                new StripedResourceLockManager(), Clock.systemUTC());
    }

    @AfterEach
    void tearDownCart() throws Exception {
        database.close();
    }

    CartService service() {
        return service;
    }
}
