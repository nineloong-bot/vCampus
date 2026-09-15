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

/** Holds dependencies and shared state for {@link SimulatedPaymentService}. */
abstract class SimulatedPaymentServiceSupport {
    protected final ShopUserPort users;
    protected final TransactionManager transactions;
    protected final ResourceLockManager locks;
    protected final Clock clock;

    /**
     * Creates a simulated payment service with its required collaborators.
     * @param users the users
     * @param transactions the transactions
     * @param locks the locks
     * @param clock the clock
     */
    protected SimulatedPaymentServiceSupport(ShopUserPort users, TransactionManager transactions,
            ResourceLockManager locks, Clock clock) {
        this.users = Objects.requireNonNull(users, "users");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.locks = Objects.requireNonNull(locks, "locks");
        this.clock = Objects.requireNonNull(clock, "clock");
    }
}
