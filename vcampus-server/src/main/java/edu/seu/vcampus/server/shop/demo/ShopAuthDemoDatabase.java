package edu.seu.vcampus.server.shop.demo;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Creates the disposable Access database used by the authenticated Shop demo. */
public final class ShopAuthDemoDatabase {
    private static final String DEMO_PASSWORD_HASH =
            "7FUgpmUKRTM7k5BqyJmwQrxgmA/3uSQ3C8yhryadIAA=";
    private static final String DEMO_PASSWORD_SALT = "AAECAwQFBgcICQoLDA0ODw==";

    private ShopAuthDemoDatabase() {
    }

    /** Replaces a dedicated demo database with a deterministic authenticated Shop fixture. */
    public static void initialize(Path database, Path schemaDirectory, Path seedDirectory)
            throws Exception {
        Path target = Objects.requireNonNull(database, "database")
                .toAbsolutePath().normalize();
        Path schemas = Objects.requireNonNull(schemaDirectory, "schemaDirectory")
                .toAbsolutePath().normalize();
        Path seeds = Objects.requireNonNull(seedDirectory, "seedDirectory")
                .toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.deleteIfExists(target);
        String url = "jdbc:ucanaccess://" + target
                + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true";
        try (Connection connection = DriverManager.getConnection(url)) {
            executeScript(connection, schemas.resolve("001_common.sql"));
            executeScript(connection, schemas.resolve("010_user.sql"));
            executeScript(connection, seeds.resolve("010_roles_permissions.sql"));
            executeScript(connection, schemas.resolve("050_shop.sql"));
            seedUsers(connection);
            seedCatalog(connection);
        }
    }

    private static void executeScript(Connection connection, Path script) throws Exception {
        for (String sql : Files.readString(script).split(";")) {
            if (!sql.isBlank()) {
                try (var statement = connection.createStatement()) {
                    statement.execute(sql.strip());
                }
            }
        }
    }

    private static void seedUsers(Connection connection) throws Exception {
        insertUser(connection, "demo-buyer", "DEMO_BUYER");
        insertUser(connection, "demo-owner-stationery", "DEMO_OWNER_STATIONERY");
        insertUser(connection, "demo-owner-books", "DEMO_OWNER_BOOKS");
    }

    private static void insertUser(Connection connection, String userId, String loginId)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, "
                        + "passwordIterations, roleCode, accountStatus, mustChangePassword, "
                        + "failedLoginCount, rowVersion, createdAt, updatedAt) "
                        + "VALUES (?, ?, ?, ?, 120000, 'STUDENT', 'ACTIVE', FALSE, 0, 0, "
                        + "NOW(), NOW())")) {
            statement.setString(1, userId);
            statement.setString(2, loginId);
            statement.setString(3, DEMO_PASSWORD_HASH);
            statement.setString(4, DEMO_PASSWORD_SALT);
            statement.executeUpdate();
        }
    }

    private static void seedCatalog(Connection connection) throws Exception {
        Instant now = Instant.now();
        insertShop(connection, "demo-shop-stationery", "demo-owner-stationery",
                "校园文具店", now);
        insertShop(connection, "demo-shop-books", "demo-owner-books", "校园书店", now);
        insertProduct(connection, "demo-pen", "demo-shop-stationery", "签字笔", now);
        insertProduct(connection, "demo-book", "demo-shop-books", "Java 教材", now);
        for (SkuSeed sku : List.of(
                new SkuSeed("demo-pen-black", "demo-pen", "黑色", "3.00", 10, true),
                new SkuSeed("demo-pen-low-stock", "demo-pen", "限量红色", "4.00", 1, true),
                new SkuSeed("demo-pen-retired", "demo-pen", "停产蓝色", "3.00", 6, false),
                new SkuSeed("demo-book-standard", "demo-book", "标准版", "10.00", 5, true))) {
            insertSku(connection, sku);
        }
    }

    private static void insertShop(Connection connection, String id, String owner,
            String name, Instant now) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO tblShop (shopId, ownerUserId, shopName, description, category, "
                        + "contact, shopStatus, rowVersion, createdAt, updatedAt) "
                        + "VALUES (?, ?, ?, 'Demo 店铺', '校园生活', 'demo@example.com', "
                        + "'ACTIVE', 0, ?, ?)")) {
            statement.setString(1, id);
            statement.setString(2, owner);
            statement.setString(3, name);
            statement.setTimestamp(4, Timestamp.from(now));
            statement.setTimestamp(5, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    private static void insertProduct(Connection connection, String id, String shopId,
            String name, Instant now) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO tblProduct (productId, shopId, productName, category, description, "
                        + "productStatus, salesCount, rowVersion, createdAt, updatedAt) "
                        + "VALUES (?, ?, ?, '校园生活', 'Demo 商品', 'ACTIVE', 0, 0, ?, ?)")) {
            statement.setString(1, id);
            statement.setString(2, shopId);
            statement.setString(3, name);
            statement.setTimestamp(4, Timestamp.from(now));
            statement.setTimestamp(5, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    private static void insertSku(Connection connection, SkuSeed sku) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO tblProductSku (skuId, productId, skuName, unitPrice, "
                        + "stockQuantity, reservedQuantity, isActive, rowVersion) "
                        + "VALUES (?, ?, ?, ?, ?, 0, ?, 0)")) {
            statement.setString(1, sku.id());
            statement.setString(2, sku.productId());
            statement.setString(3, sku.name());
            statement.setBigDecimal(4, new BigDecimal(sku.price()));
            statement.setLong(5, sku.stock());
            statement.setBoolean(6, sku.active());
            statement.executeUpdate();
        }
    }

    private record SkuSeed(String id, String productId, String name, String price,
            long stock, boolean active) {
    }
}
