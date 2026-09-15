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

/** Implements focused public operations for {@link SimulatedPaymentService}. */
abstract class SimulatedPaymentServiceOperations1 extends SimulatedPaymentServiceHelpers1 {
    protected SimulatedPaymentServiceOperations1(ShopUserPort users, TransactionManager transactions,
            ResourceLockManager locks, Clock clock) {
        super(users, transactions, locks, clock);
    }


    /**
     * Performs the simulate payment operation.
     * @param sessionToken the session token
     * @param command the command
     * @return the operation result
     */
    public PaymentView simulatePayment(String sessionToken, SimulatePaymentCommand command) {
        ShopUser buyer = BuyerGuard.requireBuyer(requireActiveUser(sessionToken));
        validate(command);
        PaymentLockData lockData = transactions.inTransaction(connection ->
                loadLockData(connection, command.paymentId(), buyer.userId()));
        return locks.withLocks(lockData.keys(), () -> transactions.inTransaction(connection ->
                applyAttempt(connection, buyer.userId(), command)));
    }
}
