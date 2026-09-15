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

/** Provides focused helper operations for {@link CheckoutService}. */
abstract class CheckoutServiceHelpers3 extends CheckoutServiceHelpers4 {
    protected CheckoutServiceHelpers3(ShopRepository repository, ShopUserPort users,
            TransactionManager transactions, ResourceLockManager locks, Clock clock) {
        super(repository, users, transactions, locks, clock);
    }


    protected static List<CheckoutLine> uniqueSkuLines(List<CheckoutLine> lines) {
        Set<String> seen = new LinkedHashSet<>();
        return lines.stream().filter(line -> seen.add(line.skuId())).toList();
    }

    protected static void insertOrderGroup(Connection connection, String id, String buyerId,
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

    protected static void insertOrder(Connection connection, String id, String groupId,
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

    protected static void insertOrderItem(Connection connection, String orderId,
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

    protected static void insertPayment(Connection connection, String id, String groupId,
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
}
