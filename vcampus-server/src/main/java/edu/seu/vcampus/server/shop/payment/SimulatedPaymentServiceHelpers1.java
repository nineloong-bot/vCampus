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
abstract class SimulatedPaymentServiceHelpers1 extends SimulatedPaymentServiceHelpers2 {
    protected SimulatedPaymentServiceHelpers1(ShopUserPort users, TransactionManager transactions,
            ResourceLockManager locks, Clock clock) {
        super(users, transactions, locks, clock);
    }


    protected PaymentView applyAttempt(Connection connection, String buyerId,
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

    protected static PaymentView succeed(Connection connection, PaymentRecord payment,
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

    protected static PaymentView cancel(Connection connection, PaymentRecord payment,
            Instant now) throws Exception {
        releaseReservations(connection, payment.paymentId(), now);
        updateAggregateStates(connection, payment, PaymentStatus.CANCELLED,
                "CANCELLED", null, now);
        return toView(loadPayment(connection, payment.paymentId(), payment.buyerUserId()));
    }
}
