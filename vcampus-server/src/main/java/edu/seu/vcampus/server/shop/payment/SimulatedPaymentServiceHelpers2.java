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
abstract class SimulatedPaymentServiceHelpers2 extends SimulatedPaymentServiceHelpers3 {
    protected SimulatedPaymentServiceHelpers2(ShopUserPort users, TransactionManager transactions,
            ResourceLockManager locks, Clock clock) {
        super(users, transactions, locks, clock);
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

    protected static PaymentLockData lockData(PaymentRecord payment,
            List<Reservation> reservations) {
        List<ResourceKey> keys = new ArrayList<>();
        keys.add(new ResourceKey("PAYMENT", payment.paymentId()));
        keys.add(new ResourceKey("ORDER_GROUP", payment.orderGroupId()));
        reservations.stream().map(Reservation::skuId).distinct().sorted()
                .map(id -> new ResourceKey("SKU", id)).forEach(keys::add);
        return new PaymentLockData(payment.paymentId(), payment.orderGroupId(), keys);
    }
}
