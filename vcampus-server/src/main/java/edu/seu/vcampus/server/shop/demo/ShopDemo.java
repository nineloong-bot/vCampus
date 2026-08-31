package edu.seu.vcampus.server.shop.demo;

import edu.seu.vcampus.common.shop.AddCartItemCommand;
import edu.seu.vcampus.common.shop.CheckoutCommand;
import edu.seu.vcampus.common.shop.CheckoutItem;
import edu.seu.vcampus.common.shop.HomeProductQuery;
import edu.seu.vcampus.common.shop.PaymentAttemptStatus;
import edu.seu.vcampus.common.shop.PaymentChannel;
import edu.seu.vcampus.common.shop.ProductSortMode;
import edu.seu.vcampus.common.shop.SimulatePaymentCommand;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.persistence.ConnectionProvider;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.shop.payment.SimulatedPaymentService;
import edu.seu.vcampus.server.shop.repository.AccessShopRepository;
import edu.seu.vcampus.server.shop.service.CartService;
import edu.seu.vcampus.server.shop.service.CheckoutService;
import edu.seu.vcampus.server.shop.service.ShopService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** Builds a persistent Access database and runs a complete Shop purchase scenario. */
public final class ShopDemo {
    public static final String BUYER_TOKEN = "demo-buyer-token";

    private ShopDemo() {
    }

    public static void main(String[] args) throws Exception {
        Path database = Path.of(args.length > 0
                ? args[0] : "vcampus-database/demo/vcampus-shop-demo.accdb");
        Path schemaDirectory = Path.of(args.length > 1
                ? args[1] : "vcampus-database/schema");
        ShopDemoResult result = run(database, schemaDirectory);
        System.out.println("Shop Demo completed");
        System.out.println("Database: " + result.databasePath());
        System.out.println("Catalog products: " + result.catalogProductCount());
        System.out.println("Cross-shop orders: " + result.orderCount());
        System.out.println("Payment number: " + result.paymentNumber());
        System.out.println("Payment amount: CNY " + result.totalAmount());
        System.out.println("Payment status: " + result.paymentStatus());
    }

    public static ShopDemoResult run(Path databasePath, Path schemaDirectory) throws Exception {
        Path database = databasePath.toAbsolutePath().normalize();
        Path schemas = schemaDirectory.toAbsolutePath().normalize();
        Files.createDirectories(database.getParent());
        Files.deleteIfExists(database);
        String url = "jdbc:ucanaccess://" + database + ";newDatabaseVersion=V2010";
        initialize(url, schemas);

        ConnectionProvider connections = () -> DriverManager.getConnection(
                "jdbc:ucanaccess://" + database);
        TransactionManager transactions = new TransactionManager(connections);
        var repository = new AccessShopRepository();
        var users = new DemoShopUserPort();
        var locks = new StripedResourceLockManager();
        Clock clock = Clock.systemUTC();
        ShopService shop = new ShopService(repository, transactions);
        CartService cart = new CartService(repository, users, transactions, locks, clock);
        CheckoutService checkout = new CheckoutService(
                repository, users, transactions, locks, clock);
        SimulatedPaymentService payment = new SimulatedPaymentService(
                users, transactions, locks, clock);

        var catalog = shop.getHomeProducts(new HomeProductQuery(
                null, null, ProductSortMode.SALES_DESC, 0, 20));
        cart.addToCart(BUYER_TOKEN, new AddCartItemCommand("demo-pen-black", 2));
        var cartView = cart.addToCart(BUYER_TOKEN,
                new AddCartItemCommand("demo-book-standard", 1));
        var checkoutResult = checkout.checkout(BUYER_TOKEN,
                new CheckoutCommand(cartView.items().stream()
                        .map(item -> new CheckoutItem(
                                item.cartItemId(), item.displayedUnitPrice()))
                        .toList(), false));
        var paymentView = payment.simulatePayment(BUYER_TOKEN,
                new SimulatePaymentCommand(checkoutResult.paymentId(),
                        PaymentChannel.ALIPAY, PaymentAttemptStatus.SUCCEEDED));
        return new ShopDemoResult(database, catalog.items().size(),
                checkoutResult.orders().size(), paymentView.paymentNumber(),
                paymentView.amount(), paymentView.status());
    }

    private static void initialize(String url, Path schemas) throws Exception {
        try (Connection connection = DriverManager.getConnection(url)) {
            execute(connection, "CREATE TABLE tblUser (userId VARCHAR(36) PRIMARY KEY)");
            executeScript(connection, schemas.resolve("001_common.sql"));
            executeScript(connection, schemas.resolve("050_shop.sql"));
            seedUsers(connection);
            seedCatalog(connection);
        }
    }

    private static void executeScript(Connection connection, Path path) throws Exception {
        String script = Files.readString(path);
        for (String sql : script.split(";")) {
            if (!sql.isBlank()) {
                execute(connection, sql.strip());
            }
        }
    }

    private static void execute(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static void seedUsers(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO tblUser (userId) VALUES (?)")) {
            for (String id : List.of("demo-buyer", "demo-owner-stationery",
                    "demo-owner-books", "demo-admin")) {
                statement.setString(1, id);
                statement.executeUpdate();
            }
        }
    }

    private static void seedCatalog(Connection connection) throws Exception {
        Instant now = Instant.now();
        insertShop(connection, "demo-shop-stationery", "demo-owner-stationery",
                "校园文具店", now);
        insertShop(connection, "demo-shop-books", "demo-owner-books",
                "校园书店", now);
        insertProduct(connection, "demo-pen", "demo-shop-stationery", "签字笔", now);
        insertProduct(connection, "demo-book", "demo-shop-books", "Java 教材", now);
        insertSku(connection, "demo-pen-black", "demo-pen", "黑色", "3.00", 10);
        insertSku(connection, "demo-book-standard", "demo-book", "标准版", "10.00", 5);
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
                "INSERT INTO tblProduct (productId, shopId, productName, normalizedProductName, category, "
                        + "description, coverImageUrl, productStatus, salesCount, rowVersion, createdAt, updatedAt) "
                        + "VALUES (?, ?, ?, ?, '生活用品', 'Demo 商品', NULL, 'ACTIVE', 0, 0, ?, ?)")) {
            statement.setString(1, id);
            statement.setString(2, shopId);
            statement.setString(3, name);
            statement.setString(4, name.toLowerCase(java.util.Locale.ROOT));
            statement.setTimestamp(5, Timestamp.from(now));
            statement.setTimestamp(6, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    private static void insertSku(Connection connection, String id, String productId,
            String name, String price, long stock) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO tblProductSku (skuId, productId, skuName, unitPrice, "
                        + "stockQuantity, reservedQuantity, isActive, rowVersion) "
                        + "VALUES (?, ?, ?, ?, ?, 0, TRUE, 0)")) {
            statement.setString(1, id);
            statement.setString(2, productId);
            statement.setString(3, name);
            statement.setBigDecimal(4, new java.math.BigDecimal(price));
            statement.setLong(5, stock);
            statement.executeUpdate();
        }
    }
}
