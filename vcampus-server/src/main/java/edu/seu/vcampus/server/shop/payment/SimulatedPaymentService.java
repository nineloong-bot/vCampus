package edu.seu.vcampus.server.shop.payment;

import edu.seu.vcampus.common.shop.PaymentAttemptStatus;
import edu.seu.vcampus.common.shop.PaymentChannel;
import edu.seu.vcampus.common.shop.PaymentStatus;
import edu.seu.vcampus.common.shop.PaymentView;
import edu.seu.vcampus.common.shop.ShopErrorCode;
import edu.seu.vcampus.common.shop.SimulatePaymentCommand;
import edu.seu.vcampus.server.concurrency.ResourceKey;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.shop.ShopException;
import edu.seu.vcampus.server.shop.port.ShopUser;
import edu.seu.vcampus.server.shop.port.ShopUserPort;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Retryable simulated checkout payment with atomic inventory transitions. */
public final class SimulatedPaymentService {
    private final ShopUserPort users;
    private final TransactionManager transactions;
    private final ResourceLockManager locks;
    private final Clock clock;

    public SimulatedPaymentService(ShopUserPort users, TransactionManager transactions,
            ResourceLockManager locks, Clock clock) {
        this.users = Objects.requireNonNull(users, "users");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.locks = Objects.requireNonNull(locks, "locks");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public PaymentView simulatePayment(String sessionToken, SimulatePaymentCommand command) {
        ShopUser buyer = requireActiveUser(sessionToken);
        validate(command);
        PaymentLockData lockData = transactions.inTransaction(connection ->
                loadLockData(connection, command.paymentId(), buyer.userId()));
        return locks.withLocks(lockData.keys(), () -> transactions.inTransaction(connection ->
                applyAttempt(connection, buyer.userId(), command)));
    }

    private PaymentView applyAttempt(Connection connection, String buyerId,
            SimulatePaymentCommand command) throws Exception {
        PaymentRecord payment = loadPayment(connection, command.paymentId(), buyerId);
        if (payment.status() == PaymentStatus.SUCCEEDED) {
            throw error(ShopErrorCode.PAYMENT_ALREADY_COMPLETED, "Payment already completed");
        }
        if (payment.status() != PaymentStatus.PENDING) {
            throw error(ShopErrorCode.PAYMENT_NOT_PENDING, "Payment is not pending");
        }
        requireAmountInvariant(connection, payment);
        Instant now = clock.instant();
        insertAttempt(connection, payment.paymentId(), command.channel(),
                command.simulatedResult(), now);
        return switch (command.simulatedResult()) {
            case FAILED -> toView(payment);
            case SUCCEEDED -> succeed(connection, payment, command.channel(), now);
            case CANCELLED -> cancel(connection, payment, now);
            case STARTED -> throw new IllegalArgumentException("STARTED is not a final result");
        };
    }

    private static PaymentView succeed(Connection connection, PaymentRecord payment,
            PaymentChannel channel, Instant now) throws Exception {
        List<Reservation> reservations = loadReservations(connection, payment.paymentId());
        if (reservations.isEmpty()) {
            throw error(ShopErrorCode.PAYMENT_NOT_PENDING, "Payment has no active reservation");
        }
        for (Reservation reservation : reservations) {
            if (!"ACTIVE".equals(reservation.status()) || !now.isBefore(reservation.expiresAt())) {
                throw error(ShopErrorCode.PAYMENT_NOT_PENDING, "Inventory reservation expired");
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE tblProductSku SET stockQuantity = stockQuantity - ?, "
                            + "reservedQuantity = reservedQuantity - ?, rowVersion = rowVersion + 1 "
                            + "WHERE skuId = ? AND stockQuantity >= ? AND reservedQuantity >= ?")) {
                statement.setLong(1, reservation.quantity());
                statement.setLong(2, reservation.quantity());
                statement.setString(3, reservation.skuId());
                statement.setLong(4, reservation.quantity());
                statement.setLong(5, reservation.quantity());
                if (statement.executeUpdate() != 1) {
                    throw error(ShopErrorCode.SHOP_INSUFFICIENT_STOCK,
                            "Reserved inventory is inconsistent");
                }
            }
        }
        updateReservations(connection, payment.paymentId(), "CONSUMED", now);
        incrementSales(connection, payment.orderGroupId());
        updateAggregateStates(connection, payment, PaymentStatus.SUCCEEDED,
                "PAID", channel, now);
        return toView(loadPayment(connection, payment.paymentId(), payment.buyerUserId()));
    }

    private static PaymentView cancel(Connection connection, PaymentRecord payment,
            Instant now) throws Exception {
        releaseReservations(connection, payment.paymentId(), now);
        updateAggregateStates(connection, payment, PaymentStatus.CANCELLED,
                "CANCELLED", null, now);
        return toView(loadPayment(connection, payment.paymentId(), payment.buyerUserId()));
    }

    static void releaseReservations(Connection connection, String paymentId, Instant now)
            throws Exception {
        for (Reservation reservation : loadReservations(connection, paymentId)) {
            if (!"ACTIVE".equals(reservation.status())) {
                continue;
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE tblProductSku SET reservedQuantity = reservedQuantity - ?, "
                            + "rowVersion = rowVersion + 1 WHERE skuId = ? "
                            + "AND reservedQuantity >= ?")) {
                statement.setLong(1, reservation.quantity());
                statement.setString(2, reservation.skuId());
                statement.setLong(3, reservation.quantity());
                if (statement.executeUpdate() != 1) {
                    throw new IllegalStateException("Reserved inventory is inconsistent");
                }
            }
        }
        updateReservations(connection, paymentId, "RELEASED", now);
    }

    static void updateExpiredStates(Connection connection, PaymentRecord payment,
            Instant now) throws Exception {
        updateAggregateStates(connection, payment, PaymentStatus.EXPIRED,
                "CANCELLED", null, now);
    }

    static PaymentLockData loadLockData(Connection connection, String paymentId,
            String buyerId) throws Exception {
        PaymentRecord payment = loadPayment(connection, paymentId, buyerId);
        return lockData(payment, loadReservations(connection, paymentId));
    }

    static PaymentLockData loadLockData(Connection connection, String paymentId)
            throws Exception {
        PaymentRecord payment = loadPayment(connection, paymentId, null);
        return lockData(payment, loadReservations(connection, paymentId));
    }

    private static PaymentLockData lockData(PaymentRecord payment,
            List<Reservation> reservations) {
        List<ResourceKey> keys = new ArrayList<>();
        keys.add(new ResourceKey("PAYMENT", payment.paymentId()));
        keys.add(new ResourceKey("ORDER_GROUP", payment.orderGroupId()));
        reservations.stream().map(Reservation::skuId).distinct().sorted()
                .map(id -> new ResourceKey("SKU", id)).forEach(keys::add);
        return new PaymentLockData(payment.paymentId(), payment.orderGroupId(), keys);
    }

    static PaymentRecord loadPayment(Connection connection, String paymentId,
            String buyerId) throws Exception {
        String sql = "SELECT p.paymentId, p.orderGroupId, p.paymentNumber, p.amount, "
                + "p.paymentStatus, p.successfulChannel, p.completedAt, p.rowVersion, "
                + "g.buyerUserId, MIN(r.expiresAt) AS expiresAt "
                + "FROM (tblPayment p INNER JOIN tblOrderGroup g "
                + "ON p.orderGroupId = g.orderGroupId) LEFT JOIN tblInventoryReservation r "
                + "ON p.paymentId = r.paymentId WHERE p.paymentId = ? "
                + "GROUP BY p.paymentId, p.orderGroupId, p.paymentNumber, p.amount, "
                + "p.paymentStatus, p.successfulChannel, p.completedAt, p.rowVersion, g.buyerUserId";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, paymentId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SecurityException("Payment is not owned by buyer");
                }
                String owner = result.getString("buyerUserId");
                if (buyerId != null && !buyerId.equals(owner)) {
                    throw new SecurityException("Payment is not owned by buyer");
                }
                String channel = result.getString("successfulChannel");
                Timestamp completedAt = result.getTimestamp("completedAt");
                Timestamp expiresAt = result.getTimestamp("expiresAt");
                return new PaymentRecord(result.getString("paymentId"),
                        result.getString("orderGroupId"), owner,
                        result.getString("paymentNumber"), result.getBigDecimal("amount"),
                        PaymentStatus.valueOf(result.getString("paymentStatus")),
                        channel == null ? null : PaymentChannel.valueOf(channel),
                        expiresAt == null ? null : expiresAt.toInstant(),
                        completedAt == null ? null : completedAt.toInstant(),
                        result.getLong("rowVersion"));
            }
        }
    }

    static List<Reservation> loadReservations(Connection connection, String paymentId)
            throws Exception {
        List<Reservation> reservations = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT reservationId, skuId, quantity, reservationStatus, expiresAt "
                        + "FROM tblInventoryReservation WHERE paymentId = ? ORDER BY skuId")) {
            statement.setString(1, paymentId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    reservations.add(new Reservation(result.getString("reservationId"),
                            result.getString("skuId"), result.getLong("quantity"),
                            result.getString("reservationStatus"),
                            result.getTimestamp("expiresAt").toInstant()));
                }
            }
        }
        return reservations;
    }

    private static void requireAmountInvariant(Connection connection, PaymentRecord payment)
            throws Exception {
        BigDecimal groupAmount;
        BigDecimal orderAmount;
        try (PreparedStatement group = connection.prepareStatement(
                "SELECT totalAmount FROM tblOrderGroup WHERE orderGroupId = ?")) {
            group.setString(1, payment.orderGroupId());
            try (ResultSet result = group.executeQuery()) {
                result.next();
                groupAmount = result.getBigDecimal(1);
            }
        }
        try (PreparedStatement orders = connection.prepareStatement(
                "SELECT SUM(orderAmount) FROM tblOrder WHERE orderGroupId = ?")) {
            orders.setString(1, payment.orderGroupId());
            try (ResultSet result = orders.executeQuery()) {
                result.next();
                orderAmount = result.getBigDecimal(1);
            }
        }
        if (payment.amount().compareTo(groupAmount) != 0
                || orderAmount == null || groupAmount.compareTo(orderAmount) != 0) {
            throw error(ShopErrorCode.PAYMENT_AMOUNT_MISMATCH,
                    "Payment amount does not match order group");
        }
    }

    private static void insertAttempt(Connection connection, String paymentId,
            PaymentChannel channel, PaymentAttemptStatus result, Instant now) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO tblPaymentAttempt (attemptId, paymentId, channel, attemptStatus, "
                        + "createdAt, completedAt) VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, paymentId);
            statement.setString(3, channel.name());
            statement.setString(4, result.name());
            statement.setTimestamp(5, Timestamp.from(now));
            statement.setTimestamp(6, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    private static void incrementSales(Connection connection, String orderGroupId)
            throws Exception {
        String sql = "SELECT s.productId, SUM(oi.quantity) AS purchasedQuantity "
                + "FROM (tblOrder o INNER JOIN tblOrderItem oi ON o.orderId = oi.orderId) "
                + "INNER JOIN tblProductSku s ON oi.skuId = s.skuId "
                + "WHERE o.orderGroupId = ? GROUP BY s.productId";
        List<ProductSale> sales = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, orderGroupId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    sales.add(new ProductSale(result.getString("productId"),
                            result.getLong("purchasedQuantity")));
                }
            }
        }
        for (ProductSale sale : sales) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE tblProduct SET salesCount = salesCount + ?, "
                            + "rowVersion = rowVersion + 1 WHERE productId = ?")) {
                statement.setLong(1, sale.quantity());
                statement.setString(2, sale.productId());
                if (statement.executeUpdate() != 1) {
                    throw new IllegalStateException("Order product no longer exists");
                }
            }
        }
    }

    private static void updateAggregateStates(Connection connection, PaymentRecord payment,
            PaymentStatus paymentStatus, String orderStatus, PaymentChannel channel,
            Instant now) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE tblPayment SET paymentStatus = ?, successfulChannel = ?, "
                        + "completedAt = ?, rowVersion = rowVersion + 1 "
                        + "WHERE paymentId = ? AND paymentStatus = 'PENDING'")) {
            statement.setString(1, paymentStatus.name());
            statement.setString(2, channel == null ? null : channel.name());
            statement.setTimestamp(3, Timestamp.from(now));
            statement.setString(4, payment.paymentId());
            if (statement.executeUpdate() != 1) {
                throw error(ShopErrorCode.PAYMENT_NOT_PENDING, "Payment is not pending");
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE tblOrderGroup SET groupStatus = ?, rowVersion = rowVersion + 1 "
                        + "WHERE orderGroupId = ?")) {
            statement.setString(1, orderStatus);
            statement.setString(2, payment.orderGroupId());
            statement.executeUpdate();
        }
        String paidAt = paymentStatus == PaymentStatus.SUCCEEDED ? ", paidAt = ?" : "";
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE tblOrder SET orderStatus = ?" + paidAt
                        + ", rowVersion = rowVersion + 1 WHERE orderGroupId = ?")) {
            statement.setString(1, orderStatus);
            int groupIndex = 2;
            if (paymentStatus == PaymentStatus.SUCCEEDED) {
                statement.setTimestamp(2, Timestamp.from(now));
                groupIndex = 3;
            }
            statement.setString(groupIndex, payment.orderGroupId());
            statement.executeUpdate();
        }
    }

    private static void updateReservations(Connection connection, String paymentId,
            String status, Instant now) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE tblInventoryReservation SET reservationStatus = ?, releasedAt = ? "
                        + "WHERE paymentId = ? AND reservationStatus = 'ACTIVE'")) {
            statement.setString(1, status);
            statement.setTimestamp(2, Timestamp.from(now));
            statement.setString(3, paymentId);
            statement.executeUpdate();
        }
    }

    static PaymentView toView(PaymentRecord payment) {
        return new PaymentView(payment.paymentId(), payment.orderGroupId(),
                payment.paymentNumber(), payment.amount(), payment.status(),
                payment.channel(), payment.expiresAt(), payment.completedAt(),
                payment.rowVersion());
    }

    private ShopUser requireActiveUser(String sessionToken) {
        ShopUser user = users.requireUser(sessionToken);
        if (!user.active()) {
            throw new SecurityException("Active account required");
        }
        return user;
    }

    private static void validate(SimulatePaymentCommand command) {
        Objects.requireNonNull(command, "command");
        if (command.paymentId() == null || command.paymentId().isBlank()) {
            throw new IllegalArgumentException("paymentId is required");
        }
        Objects.requireNonNull(command.channel(), "channel");
        Objects.requireNonNull(command.simulatedResult(), "simulatedResult");
        if (command.simulatedResult() == PaymentAttemptStatus.STARTED) {
            throw new IllegalArgumentException("STARTED is not a final result");
        }
    }

    private static ShopException error(ShopErrorCode code, String message) {
        return new ShopException(code, message);
    }

    record PaymentLockData(String paymentId, String orderGroupId, List<ResourceKey> keys) { }

    record PaymentRecord(String paymentId, String orderGroupId, String buyerUserId,
            String paymentNumber, BigDecimal amount, PaymentStatus status,
            PaymentChannel channel, Instant expiresAt, Instant completedAt,
            long rowVersion) { }

    record Reservation(String reservationId, String skuId, long quantity,
            String status, Instant expiresAt) { }

    private record ProductSale(String productId, long quantity) { }
}
