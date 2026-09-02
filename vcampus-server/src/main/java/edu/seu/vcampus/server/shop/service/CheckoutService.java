package edu.seu.vcampus.server.shop.service;

import edu.seu.vcampus.common.shop.CheckoutCommand;
import edu.seu.vcampus.common.shop.CheckoutItem;
import edu.seu.vcampus.common.shop.CheckoutResult;
import edu.seu.vcampus.common.shop.OrderStatus;
import edu.seu.vcampus.common.shop.OrderSummary;
import edu.seu.vcampus.common.shop.ShopErrorCode;
import edu.seu.vcampus.server.concurrency.ResourceKey;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.shop.domain.CartItem;
import edu.seu.vcampus.server.shop.port.ShopUser;
import edu.seu.vcampus.server.shop.port.ShopUserPort;
import edu.seu.vcampus.server.shop.repository.ShopRepository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Creates an all-or-nothing order group from selected, buyer-owned cart items. */
public final class CheckoutService {
    private static final Duration RESERVATION_DURATION = Duration.ofMinutes(15);

    private final ShopRepository repository;
    private final ShopUserPort users;
    private final TransactionManager transactions;
    private final ResourceLockManager locks;
    private final Clock clock;

    public CheckoutService(ShopRepository repository, ShopUserPort users,
            TransactionManager transactions, ResourceLockManager locks, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.users = Objects.requireNonNull(users, "users");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.locks = Objects.requireNonNull(locks, "locks");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CheckoutResult checkout(String sessionToken, CheckoutCommand command) {
        ShopUser buyer = requireBuyer(sessionToken);
        Map<String, CheckoutItem> requested = validate(command);
        List<String> skuIds = transactions.inTransaction(connection ->
                resolveOwnedSkuIds(connection, buyer.userId(), requested.keySet()));

        List<ResourceKey> keys = skuIds.stream().distinct().sorted()
                .map(id -> SellerApplicationService.key("SKU", id))
                .collect(Collectors.toCollection(ArrayList::new));
        keys.add(SellerApplicationService.key("CART", buyer.userId()));
        return locks.withLocks(keys, () -> transactions.inTransaction(connection ->
                createCheckout(connection, buyer.userId(), requested,
                        command.acceptLatestPrice())));
    }

    private CheckoutResult createCheckout(Connection connection, String buyerId,
            Map<String, CheckoutItem> requested, boolean acceptLatestPrice) throws Exception {
        List<CheckoutLine> lines = loadLines(connection, buyerId, requested.keySet());
        if (lines.size() != requested.size()) {
            throw emptyCart("A selected cart item no longer belongs to the buyer");
        }
        Map<String, Long> quantitiesBySku = new HashMap<>();
        for (CheckoutLine line : lines) {
            BuyerGuard.requireDifferentOwner(buyerId, line.ownerUserId());
            requireSellable(line);
            BigDecimal displayed = requested.get(line.cartItemId()).displayedUnitPrice();
            if (!acceptLatestPrice && displayed.compareTo(line.unitPrice()) != 0) {
                throw SellerApplicationService.error(ShopErrorCode.SHOP_PRICE_CHANGED,
                        "A selected SKU price has changed");
            }
            quantitiesBySku.merge(line.skuId(), line.quantity(), Long::sum);
        }
        for (CheckoutLine line : uniqueSkuLines(lines)) {
            long quantity = quantitiesBySku.get(line.skuId());
            if (quantity > line.stockQuantity() - line.reservedQuantity()) {
                throw SellerApplicationService.error(ShopErrorCode.SHOP_INSUFFICIENT_STOCK,
                        "A selected SKU has insufficient stock");
            }
        }

        Instant now = clock.instant();
        Instant expiresAt = now.plus(RESERVATION_DURATION);
        String orderGroupId = UUID.randomUUID().toString();
        String paymentId = UUID.randomUUID().toString();
        String paymentNumber = number("P");
        Map<String, List<CheckoutLine>> byShop = lines.stream()
                .collect(Collectors.groupingBy(CheckoutLine::shopId,
                        LinkedHashMap::new, Collectors.toList()));
        BigDecimal total = lines.stream().map(CheckoutLine::lineAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        insertOrderGroup(connection, orderGroupId, buyerId, total, now);
        List<OrderSummary> summaries = new ArrayList<>();
        for (Map.Entry<String, List<CheckoutLine>> entry : byShop.entrySet()) {
            String orderId = UUID.randomUUID().toString();
            String orderNumber = number("O");
            List<CheckoutLine> shopLines = entry.getValue();
            BigDecimal orderAmount = shopLines.stream().map(CheckoutLine::lineAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            insertOrder(connection, orderId, orderGroupId, entry.getKey(), orderNumber,
                    orderAmount, now);
            for (CheckoutLine line : shopLines) {
                insertOrderItem(connection, orderId, line);
            }
            summaries.add(new OrderSummary(orderId, orderGroupId, orderNumber,
                    entry.getKey(), shopLines.getFirst().shopName(), orderAmount,
                    OrderStatus.PENDING_PAYMENT, now));
        }
        insertPayment(connection, paymentId, orderGroupId, paymentNumber, total);
        for (CheckoutLine line : uniqueSkuLines(lines)) {
            long quantity = quantitiesBySku.get(line.skuId());
            reserveSku(connection, line.skuId(), quantity);
            insertReservation(connection, paymentId, line.skuId(), quantity, expiresAt);
        }
        deleteCartItems(connection, requested.keySet(), buyerId);
        return new CheckoutResult(orderGroupId, paymentId, paymentNumber,
                total, expiresAt, summaries);
    }

    private List<String> resolveOwnedSkuIds(Connection connection, String buyerId,
            Set<String> cartItemIds) throws Exception {
        String cartId = repository.findCartIdByUser(connection, buyerId)
                .orElseThrow(() -> emptyCart("No active cart"));
        List<String> skuIds = new ArrayList<>();
        for (String cartItemId : cartItemIds) {
            CartItem item = repository.findCartItemById(connection, cartItemId)
                    .orElseThrow(() -> emptyCart("Selected cart item does not exist"));
            if (!cartId.equals(item.cartId())) {
                throw emptyCart("Selected cart item is not owned by buyer");
            }
            skuIds.add(item.skuId());
        }
        return skuIds;
    }

    private static List<CheckoutLine> loadLines(Connection connection, String buyerId,
            Set<String> cartItemIds) throws Exception {
        String sql = "SELECT ci.cartItemId, ci.skuId, ci.quantity, s.skuName, s.unitPrice, "
                + "s.stockQuantity, s.reservedQuantity, s.isActive, p.productId, "
                + "p.productName, p.productStatus, sh.shopId, sh.ownerUserId, sh.shopName, sh.shopStatus "
                + "FROM (((tblCartItem ci INNER JOIN tblCart c ON ci.cartId = c.cartId) "
                + "INNER JOIN tblProductSku s ON ci.skuId = s.skuId) "
                + "INNER JOIN tblProduct p ON s.productId = p.productId) "
                + "INNER JOIN tblShop sh ON p.shopId = sh.shopId "
                + "WHERE ci.cartItemId = ? AND c.userId = ?";
        List<CheckoutLine> lines = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (String cartItemId : cartItemIds) {
                statement.setString(1, cartItemId);
                statement.setString(2, buyerId);
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        lines.add(new CheckoutLine(result.getString("cartItemId"),
                                result.getString("skuId"), result.getString("productId"),
                                result.getString("shopId"), result.getString("productName"),
                                result.getString("ownerUserId"), result.getString("skuName"), result.getString("shopName"),
                                result.getBigDecimal("unitPrice"), result.getLong("quantity"),
                                result.getLong("stockQuantity"),
                                result.getLong("reservedQuantity"), result.getBoolean("isActive"),
                                result.getString("productStatus"),
                                result.getString("shopStatus")));
                    }
                }
            }
        }
        return lines;
    }

    private static Map<String, CheckoutItem> validate(CheckoutCommand command) {
        Objects.requireNonNull(command, "command");
        if (command.items().isEmpty()) {
            throw emptyCart("At least one cart item must be selected");
        }
        Map<String, CheckoutItem> requested = new LinkedHashMap<>();
        for (CheckoutItem item : command.items()) {
            Objects.requireNonNull(item, "checkout item");
            SellerApplicationService.requireId(item.cartItemId(), "cartItemId");
            if (item.displayedUnitPrice() == null || item.displayedUnitPrice().signum() < 0) {
                throw new IllegalArgumentException("displayedUnitPrice must be non-negative");
            }
            if (requested.putIfAbsent(item.cartItemId(), item) != null) {
                throw new IllegalArgumentException("Duplicate cartItemId");
            }
        }
        return requested;
    }

    private ShopUser requireActiveUser(String sessionToken) {
        ShopUser user = users.requireUser(sessionToken);
        if (!user.active()) {
            throw new SecurityException("Active account required");
        }
        return user;
    }

    private ShopUser requireBuyer(String sessionToken) {
        return BuyerGuard.requireBuyer(requireActiveUser(sessionToken));
    }

    private static void requireSellable(CheckoutLine line) {
        if (!"ACTIVE".equals(line.shopStatus())) {
            throw SellerApplicationService.error(ShopErrorCode.SHOP_SUSPENDED,
                    "Shop is suspended");
        }
        if (!"ACTIVE".equals(line.productStatus())) {
            throw SellerApplicationService.error(ShopErrorCode.SHOP_PRODUCT_INACTIVE,
                    "Product is inactive");
        }
        if (!line.skuActive()) {
            throw SellerApplicationService.error(ShopErrorCode.SHOP_SKU_UNAVAILABLE,
                    "SKU is unavailable");
        }
    }

    private static List<CheckoutLine> uniqueSkuLines(List<CheckoutLine> lines) {
        Set<String> seen = new LinkedHashSet<>();
        return lines.stream().filter(line -> seen.add(line.skuId())).toList();
    }

    private static void insertOrderGroup(Connection connection, String id, String buyerId,
            BigDecimal total, Instant now) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO tblOrderGroup (orderGroupId, buyerUserId, totalAmount, "
                        + "groupStatus, createdAt, rowVersion) VALUES (?, ?, ?, 'PENDING_PAYMENT', ?, 0)")) {
            statement.setString(1, id);
            statement.setString(2, buyerId);
            statement.setBigDecimal(3, total);
            statement.setTimestamp(4, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    private static void insertOrder(Connection connection, String id, String groupId,
            String shopId, String number, BigDecimal amount, Instant now) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO tblOrder (orderId, orderGroupId, shopId, orderNumber, "
                        + "orderAmount, orderStatus, createdAt, rowVersion) "
                        + "VALUES (?, ?, ?, ?, ?, 'PENDING_PAYMENT', ?, 0)")) {
            statement.setString(1, id);
            statement.setString(2, groupId);
            statement.setString(3, shopId);
            statement.setString(4, number);
            statement.setBigDecimal(5, amount);
            statement.setTimestamp(6, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    private static void insertOrderItem(Connection connection, String orderId,
            CheckoutLine line) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO tblOrderItem (orderItemId, orderId, skuId, productNameSnapshot, "
                        + "skuNameSnapshot, shopNameSnapshot, unitPrice, quantity, lineAmount) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, orderId);
            statement.setString(3, line.skuId());
            statement.setString(4, line.productName());
            statement.setString(5, line.skuName());
            statement.setString(6, line.shopName());
            statement.setBigDecimal(7, line.unitPrice());
            statement.setLong(8, line.quantity());
            statement.setBigDecimal(9, line.lineAmount());
            statement.executeUpdate();
        }
    }

    private static void insertPayment(Connection connection, String id, String groupId,
            String number, BigDecimal amount) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO tblPayment (paymentId, orderGroupId, paymentNumber, amount, "
                        + "paymentStatus, rowVersion) VALUES (?, ?, ?, ?, 'PENDING', 0)")) {
            statement.setString(1, id);
            statement.setString(2, groupId);
            statement.setString(3, number);
            statement.setBigDecimal(4, amount);
            statement.executeUpdate();
        }
    }

    private static void reserveSku(Connection connection, String skuId, long quantity)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE tblProductSku SET reservedQuantity = reservedQuantity + ?, "
                        + "rowVersion = rowVersion + 1 WHERE skuId = ? "
                        + "AND stockQuantity - reservedQuantity >= ?")) {
            statement.setLong(1, quantity);
            statement.setString(2, skuId);
            statement.setLong(3, quantity);
            if (statement.executeUpdate() != 1) {
                throw SellerApplicationService.error(ShopErrorCode.SHOP_INSUFFICIENT_STOCK,
                        "A selected SKU has insufficient stock");
            }
        }
    }

    private static void insertReservation(Connection connection, String paymentId,
            String skuId, long quantity, Instant expiresAt) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO tblInventoryReservation (reservationId, paymentId, skuId, "
                        + "quantity, reservationStatus, expiresAt) "
                        + "VALUES (?, ?, ?, ?, 'ACTIVE', ?)")) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, paymentId);
            statement.setString(3, skuId);
            statement.setLong(4, quantity);
            statement.setTimestamp(5, Timestamp.from(expiresAt));
            statement.executeUpdate();
        }
    }

    private static void deleteCartItems(Connection connection, Set<String> itemIds,
            String buyerId) throws Exception {
        String sql = "DELETE FROM tblCartItem WHERE cartItemId = ? AND cartId IN "
                + "(SELECT cartId FROM tblCart WHERE userId = ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (String itemId : itemIds) {
                statement.setString(1, itemId);
                statement.setString(2, buyerId);
                if (statement.executeUpdate() != 1) {
                    throw emptyCart("Selected cart item changed during checkout");
                }
            }
        }
    }

    private static String number(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 31);
    }

    private static RuntimeException emptyCart(String message) {
        return SellerApplicationService.error(ShopErrorCode.SHOP_CART_EMPTY, message);
    }

    private record CheckoutLine(String cartItemId, String skuId, String productId,
            String shopId, String productName, String ownerUserId, String skuName, String shopName,
            BigDecimal unitPrice, long quantity, long stockQuantity,
            long reservedQuantity, boolean skuActive, String productStatus,
            String shopStatus) {
        BigDecimal lineAmount() {
            return unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
    }
}
