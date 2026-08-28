package edu.seu.vcampus.server.shop.payment;

import edu.seu.vcampus.common.shop.PaymentStatus;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionManager;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Idempotently releases inventory for pending payments whose reservations expired. */
public final class ReservationExpiryJob {
    private final TransactionManager transactions;
    private final ResourceLockManager locks;
    private final Clock clock;

    public ReservationExpiryJob(TransactionManager transactions,
            ResourceLockManager locks, Clock clock) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.locks = Objects.requireNonNull(locks, "locks");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public int expirePendingPayments() {
        Instant now = clock.instant();
        List<String> candidates = transactions.inTransaction(connection -> {
            List<String> ids = new ArrayList<>();
            try (var statement = connection.prepareStatement(
                    "SELECT DISTINCT p.paymentId FROM tblPayment p "
                            + "INNER JOIN tblInventoryReservation r ON p.paymentId = r.paymentId "
                            + "WHERE p.paymentStatus = 'PENDING' "
                            + "AND r.reservationStatus = 'ACTIVE' AND r.expiresAt <= ?")) {
                statement.setTimestamp(1, Timestamp.from(now));
                try (var result = statement.executeQuery()) {
                    while (result.next()) {
                        ids.add(result.getString(1));
                    }
                }
            }
            return ids;
        });
        int expired = 0;
        for (String paymentId : candidates) {
            SimulatedPaymentService.PaymentLockData lockData = transactions.inTransaction(
                    connection -> SimulatedPaymentService.loadLockData(connection, paymentId));
            boolean changed = locks.withLocks(lockData.keys(), () ->
                    transactions.inTransaction(connection -> expireOne(connection, paymentId, now)));
            if (changed) {
                expired++;
            }
        }
        return expired;
    }

    private static boolean expireOne(java.sql.Connection connection, String paymentId,
            Instant now) throws Exception {
        SimulatedPaymentService.PaymentRecord payment =
                SimulatedPaymentService.loadPayment(connection, paymentId, null);
        if (payment.status() != PaymentStatus.PENDING) {
            return false;
        }
        boolean expired = SimulatedPaymentService.loadReservations(connection, paymentId).stream()
                .anyMatch(reservation -> "ACTIVE".equals(reservation.status())
                        && !reservation.expiresAt().isAfter(now));
        if (!expired) {
            return false;
        }
        SimulatedPaymentService.releaseReservations(connection, paymentId, now);
        SimulatedPaymentService.updateExpiredStates(connection, payment, now);
        return true;
    }
}
