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
abstract class SimulatedPaymentServiceHelpers3 extends SimulatedPaymentServiceHelpers4 {
    protected SimulatedPaymentServiceHelpers3(ShopUserPort users, TransactionManager transactions,
            ResourceLockManager locks, Clock clock) {
        super(users, transactions, locks, clock);
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

    protected static void requireAmountInvariant(Connection connection, PaymentRecord payment)
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
}
