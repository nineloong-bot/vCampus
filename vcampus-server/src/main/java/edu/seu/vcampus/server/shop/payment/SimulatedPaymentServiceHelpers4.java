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
import edu.seu.vcampus.server.shop.service.BuyerGuard;

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

/** Provides focused helper operations for {@link SimulatedPaymentService}. */
abstract class SimulatedPaymentServiceHelpers4 extends SimulatedPaymentServiceHelpers5 {
    protected SimulatedPaymentServiceHelpers4(ShopUserPort users, TransactionManager transactions,
            ResourceLockManager locks, Clock clock) {
        super(users, transactions, locks, clock);
    }


    protected static void insertAttempt(Connection connection, String paymentId,
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

    protected static void incrementSales(Connection connection, String orderGroupId)
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

    protected static void updateAggregateStates(Connection connection, PaymentRecord payment,
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
}
