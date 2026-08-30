package edu.seu.vcampus.server.shop.demo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;

import edu.seu.vcampus.server.shop.demo.ShopDemoCatalog.ProductSeed;
import edu.seu.vcampus.server.shop.demo.ShopDemoCatalog.SkuSeed;

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
        insertUser(connection, "demo-owner-daily", "DEMO_OWNER_DAILY");
        insertUser(connection, "demo-owner-medicine", "DEMO_OWNER_MEDICINE");
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
                "校园文具店", "文具", now);
        insertShop(connection, "demo-shop-books", "demo-owner-books", "校园书店", "图书", now);
        insertShop(connection, "demo-shop-daily", "demo-owner-daily",
                "校园生活超市", "生活用品", now);
        insertShop(connection, "demo-shop-medicine", "demo-owner-medicine",
                "校园药店", "药品", now);
        for (ProductSeed product : ShopDemoCatalog.products()) {
            insertProduct(connection, product, now);
            for (SkuSeed sku : product.skus()) {
                insertSku(connection, product.id(), sku);
            }
        }
    }

    private static void insertShop(Connection connection, String id, String owner,
            String name, String category, Instant now) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO tblShop (shopId, ownerUserId, shopName, description, category, "
                        + "contact, shopStatus, rowVersion, createdAt, updatedAt) "
                        + "VALUES (?, ?, ?, ?, ?, 'demo@example.com', "
                        + "'ACTIVE', 0, ?, ?)")) {
            statement.setString(1, id);
            statement.setString(2, owner);
            statement.setString(3, name);
            statement.setString(4, name + "认证商城 Demo 店铺");
            statement.setString(5, category);
            statement.setTimestamp(6, Timestamp.from(now));
            statement.setTimestamp(7, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    private static void insertProduct(Connection connection, ProductSeed product,
            Instant now) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO tblProduct (productId, shopId, productName, category, description, "
                        + "productStatus, salesCount, rowVersion, createdAt, updatedAt) "
                        + "VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, 0, ?, ?)")) {
            statement.setString(1, product.id());
            statement.setString(2, product.shopId());
            statement.setString(3, product.name());
            statement.setString(4, product.category());
            statement.setString(5, product.description());
            statement.setLong(6, product.salesCount());
            statement.setTimestamp(7, Timestamp.from(now));
            statement.setTimestamp(8, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    private static void insertSku(Connection connection, String productId, SkuSeed sku)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO tblProductSku (skuId, productId, skuName, unitPrice, "
                        + "stockQuantity, reservedQuantity, isActive, rowVersion) "
                        + "VALUES (?, ?, ?, ?, ?, 0, TRUE, 0)")) {
            statement.setString(1, sku.id());
            statement.setString(2, productId);
            statement.setString(3, sku.name());
            statement.setBigDecimal(4, sku.price());
            statement.setLong(5, sku.stock());
            statement.executeUpdate();
        }
    }
}
