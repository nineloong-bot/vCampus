package edu.seu.vcampus.server.shop.demo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import edu.seu.vcampus.server.shop.demo.ShopDemoCatalog.ProductSeed;
import edu.seu.vcampus.server.shop.demo.ShopDemoCatalog.SkuSeed;

/** Creates the disposable Access database used by the authenticated Shop demo. */
public final class ShopAuthDemoDatabase {
    private static final String DEMO_PASSWORD_HASH =
            "jolpzq3YokNtH5OvKaaTwkTrbGWTkJMPxIMvhsqrMw4=";
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
            seedApplications(connection);
            seedOrders(connection);
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
        insertUser(connection, "demo-buyer", "DEMO_BUYER", "STUDENT", "ACTIVE");
        insertUser(connection, "demo-other-buyer", "DEMO_OTHER_BUYER", "STUDENT", "ACTIVE");
        insertUser(connection, "demo-teacher", "DEMO_TEACHER", "TEACHER", "ACTIVE");
        insertUser(connection, "demo-admin", "DEMO_ADMIN", "ADMIN", "ACTIVE");
        insertUser(connection, "demo-owner-stationery", "DEMO_OWNER_STATIONERY",
                "STUDENT", "DISABLED");
        insertUser(connection, "demo-owner-books", "DEMO_OWNER_BOOKS",
                "STUDENT", "DISABLED");
        insertUser(connection, "demo-owner-daily", "DEMO_OWNER_DAILY",
                "STUDENT", "DISABLED");
        insertUser(connection, "demo-owner-medicine", "DEMO_OWNER_MEDICINE",
                "STUDENT", "DISABLED");
        insertUser(connection, "demo-owner-other", "DEMO_OWNER_OTHER",
                "STUDENT", "DISABLED");
    }

    private static void insertUser(Connection connection, String userId, String loginId,
            String role, String status) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO tblUser (userId, loginId, passwordHash, passwordSalt, "
                        + "passwordIterations, roleCode, accountStatus, mustChangePassword, "
                        + "failedLoginCount, rowVersion, createdAt, updatedAt) "
                        + "VALUES (?, ?, ?, ?, 120000, ?, ?, FALSE, 0, 0, "
                        + "NOW(), NOW())")) {
            statement.setString(1, userId);
            statement.setString(2, loginId);
            statement.setString(3, DEMO_PASSWORD_HASH);
            statement.setString(4, DEMO_PASSWORD_SALT);
            statement.setString(5, role);
            statement.setString(6, status);
            statement.executeUpdate();
        }
    }

    private static void seedApplications(Connection connection) throws Exception {
        Instant submitted = Instant.parse("2026-08-20T08:00:00Z");
        Instant reviewed = Instant.parse("2026-08-21T09:30:00Z");
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO tblSellerApplication (applicationId, applicantUserId, shopName, "
                        + "description, category, contact, applicationStatement, applicationStatus, "
                        + "reviewReason, reviewerUserId, submittedAt, reviewedAt, rowVersion) "
                        + "VALUES ('demo-teacher-application', 'demo-teacher', "
                        + "'教师创意用品店', '课程创意材料与教学辅助用品', '其他', "
                        + "'teacher@demo.local', '为师生提供课程项目材料与教学辅助服务', "
                        + "'REJECTED', '请补充经营时间与售后安排', 'demo-admin', ?, ?, 2)")) {
            statement.setTimestamp(1, Timestamp.from(submitted));
            statement.setTimestamp(2, Timestamp.from(reviewed));
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
        insertShop(connection, "demo-shop-other", "demo-owner-other",
                "校园综合店", "其他", now);
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
                "INSERT INTO tblShop (shopId, ownerUserId, shopName, normalizedShopName, description, category, "
                        + "contact, shopStatus, rowVersion, createdAt, updatedAt) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 'demo@example.com', "
                        + "'ACTIVE', 0, ?, ?)")) {
            statement.setString(1, id);
            statement.setString(2, owner);
            statement.setString(3, name);
            statement.setString(4, name.strip().toLowerCase(java.util.Locale.ROOT));
            statement.setString(5, name + "认证商城 Demo 店铺");
            statement.setString(6, category);
            statement.setTimestamp(7, Timestamp.from(now));
            statement.setTimestamp(8, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    private static void insertProduct(Connection connection, ProductSeed product,
            Instant now) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO tblProduct (productId, shopId, productName, normalizedProductName, category, "
                        + "description, coverImageUrl, productStatus, salesCount, rowVersion, createdAt, updatedAt) "
                        + "VALUES (?, ?, ?, ?, ?, ?, NULL, 'ACTIVE', ?, 0, ?, ?)")) {
            statement.setString(1, product.id());
            statement.setString(2, product.shopId());
            statement.setString(3, product.name());
            statement.setString(4, product.name().strip().toLowerCase(java.util.Locale.ROOT));
            statement.setString(5, product.category());
            statement.setString(6, product.description());
            statement.setLong(7, product.salesCount());
            statement.setTimestamp(8, Timestamp.from(now));
            statement.setTimestamp(9, Timestamp.from(now));
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

    private static void seedOrders(Connection connection) throws Exception {
        List<DemoOrderSeed> orders = List.of(
                new DemoOrderSeed("demo-group-buyer-paid-new",
                        "demo-order-buyer-paid-new", "DEMO-B-PAID-002",
                        "demo-item-buyer-paid-new", "demo-payment-buyer-paid-new",
                        "DEMO-PAY-B-002", "demo-attempt-buyer-paid-new",
                        "demo-buyer", "demo-daily-001", "demo-daily-001-sku-1", 3,
                        Instant.parse("2026-08-29T09:00:00Z"),
                        Instant.parse("2026-08-29T09:05:00Z"), "WECHAT"),
                new DemoOrderSeed("demo-group-buyer-paid-old",
                        "demo-order-buyer-paid-old", "DEMO-B-PAID-001",
                        "demo-item-buyer-paid-old", "demo-payment-buyer-paid-old",
                        "DEMO-PAY-B-001", "demo-attempt-buyer-paid-old",
                        "demo-buyer", "demo-stationery-002",
                        "demo-stationery-002-sku-1", 2,
                        Instant.parse("2026-08-25T08:00:00Z"),
                        Instant.parse("2026-08-25T08:05:00Z"), "ALIPAY"),
                new DemoOrderSeed("demo-group-other-paid",
                        "demo-order-other-paid", "DEMO-O-PAID-001",
                        "demo-item-other-paid", "demo-payment-other-paid",
                        "DEMO-PAY-O-001", "demo-attempt-other-paid",
                        "demo-other-buyer", "demo-books-001", "demo-books-001-sku-1", 1,
                        Instant.parse("2026-08-27T10:00:00Z"),
                        Instant.parse("2026-08-27T10:05:00Z"), "BANK_CARD"),
                new DemoOrderSeed("demo-group-buyer-pending",
                        "demo-order-buyer-pending", "DEMO-B-PENDING-001",
                        "demo-item-buyer-pending", "demo-payment-buyer-pending",
                        "DEMO-PAY-B-PENDING-001", null,
                        "demo-buyer", "demo-medicine-001", "demo-medicine-001-sku-1", 1,
                        Instant.parse("2026-08-30T07:00:00Z"), null, null));
        for (DemoOrderSeed order : orders) {
            insertOrderFixture(connection, order);
        }
    }

    private static void insertOrderFixture(Connection connection, DemoOrderSeed seed)
            throws Exception {
        ProductSeed product = ShopDemoCatalog.products().stream()
                .filter(candidate -> candidate.id().equals(seed.productId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Unknown demo product " + seed.productId()));
        SkuSeed sku = product.skus().stream()
                .filter(candidate -> candidate.id().equals(seed.skuId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown demo SKU " + seed.skuId()));
        BigDecimal amount = sku.price().multiply(BigDecimal.valueOf(seed.quantity()));
        boolean paid = seed.paidAt() != null;
        String status = paid ? "PAID" : "PENDING_PAYMENT";

        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO tblOrderGroup (orderGroupId, buyerUserId, totalAmount, "
                        + "groupStatus, createdAt, rowVersion) VALUES (?, ?, ?, ?, ?, 0)")) {
            statement.setString(1, seed.groupId());
            statement.setString(2, seed.buyerId());
            statement.setBigDecimal(3, amount);
            statement.setString(4, status);
            statement.setTimestamp(5, Timestamp.from(seed.createdAt()));
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO tblOrder (orderId, orderGroupId, shopId, orderNumber, "
                        + "orderAmount, orderStatus, createdAt, paidAt, rowVersion) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)")) {
            statement.setString(1, seed.orderId());
            statement.setString(2, seed.groupId());
            statement.setString(3, product.shopId());
            statement.setString(4, seed.orderNumber());
            statement.setBigDecimal(5, amount);
            statement.setString(6, status);
            statement.setTimestamp(7, Timestamp.from(seed.createdAt()));
            statement.setTimestamp(8, paid ? Timestamp.from(seed.paidAt()) : null);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO tblOrderItem (orderItemId, orderId, skuId, "
                        + "productNameSnapshot, skuNameSnapshot, shopNameSnapshot, "
                        + "unitPrice, quantity, lineAmount) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, seed.itemId());
            statement.setString(2, seed.orderId());
            statement.setString(3, sku.id());
            statement.setString(4, product.name());
            statement.setString(5, sku.name());
            statement.setString(6, shopName(product.shopId()));
            statement.setBigDecimal(7, sku.price());
            statement.setInt(8, seed.quantity());
            statement.setBigDecimal(9, amount);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO tblPayment (paymentId, orderGroupId, paymentNumber, "
                        + "successfulChannel, amount, paymentStatus, completedAt, rowVersion) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 0)")) {
            statement.setString(1, seed.paymentId());
            statement.setString(2, seed.groupId());
            statement.setString(3, seed.paymentNumber());
            statement.setString(4, seed.channel());
            statement.setBigDecimal(5, amount);
            statement.setString(6, paid ? "SUCCEEDED" : "PENDING");
            statement.setTimestamp(7, paid ? Timestamp.from(seed.paidAt()) : null);
            statement.executeUpdate();
        }
        if (paid) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO tblPaymentAttempt (attemptId, paymentId, channel, "
                            + "attemptStatus, createdAt, completedAt) "
                            + "VALUES (?, ?, ?, 'SUCCEEDED', ?, ?)")) {
                statement.setString(1, seed.attemptId());
                statement.setString(2, seed.paymentId());
                statement.setString(3, seed.channel());
                statement.setTimestamp(4, Timestamp.from(seed.paidAt().minusSeconds(30)));
                statement.setTimestamp(5, Timestamp.from(seed.paidAt()));
                statement.executeUpdate();
            }
        }
    }

    private static String shopName(String shopId) {
        return switch (shopId) {
            case "demo-shop-stationery" -> "校园文具店";
            case "demo-shop-books" -> "校园书店";
            case "demo-shop-daily" -> "校园生活超市";
            case "demo-shop-medicine" -> "校园药店";
            default -> throw new IllegalStateException("Unknown demo shop " + shopId);
        };
    }

    private record DemoOrderSeed(String groupId, String orderId, String orderNumber,
            String itemId, String paymentId, String paymentNumber, String attemptId,
            String buyerId, String productId, String skuId, int quantity,
            Instant createdAt, Instant paidAt, String channel) {
    }
}
