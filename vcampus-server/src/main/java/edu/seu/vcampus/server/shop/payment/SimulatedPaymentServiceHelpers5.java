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
abstract class SimulatedPaymentServiceHelpers5 extends SimulatedPaymentServiceSupport {
    protected SimulatedPaymentServiceHelpers5(ShopUserPort users, TransactionManager transactions,
            ResourceLockManager locks, Clock clock) {
        super(users, transactions, locks, clock);
    }


    protected static void updateReservations(Connection connection, String paymentId,
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

    protected ShopUser requireActiveUser(String sessionToken) {
        ShopUser user = users.requireUser(sessionToken);
        if (!user.active()) {
            throw new SecurityException("Active account required");
        }
        return user;
    }

    protected static void validate(SimulatePaymentCommand command) {
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

    protected static ShopException error(ShopErrorCode code, String message) {
        return new ShopException(code, message);
    }

    /** Provides payment lock data behavior. */
    record PaymentLockData(String paymentId, String orderGroupId, List<ResourceKey> keys) { }

    /** Provides payment record behavior. */
    record PaymentRecord(String paymentId, String orderGroupId, String buyerUserId,
            String paymentNumber, BigDecimal amount, PaymentStatus status,
            PaymentChannel channel, Instant expiresAt, Instant completedAt,
            long rowVersion) { }

    /** Provides reservation behavior. */
    record Reservation(String reservationId, String skuId, long quantity,
            String status, Instant expiresAt) { }

    /** Provides product sale behavior. */
    protected record ProductSale(String productId, long quantity) { }
}
