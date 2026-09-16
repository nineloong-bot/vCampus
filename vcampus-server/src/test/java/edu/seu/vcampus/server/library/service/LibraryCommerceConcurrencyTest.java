package edu.seu.vcampus.server.library.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.*;
import edu.seu.vcampus.common.wallet.WalletOperationResult;
import edu.seu.vcampus.server.persistence.TransactionContext;
import edu.seu.vcampus.server.library.repository.AccessLibraryFineRepository;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.wallet.service.WalletFinePort;
import edu.seu.vcampus.server.wallet.service.WalletFineService;
import java.sql.SQLException;
import java.time.Clock;
import static org.assertj.core.api.Assertions.assertThat;

class LibraryCommerceConcurrencyTest {
    @RepeatedTest(5)
    void fineAndShopPaymentCannotSpendTheSameInsufficientBalance() throws Exception {
        var f = new FineCommerceFixture();
        f.wallet.recharge("user-1", "fund", new BigDecimal("100"));
        var order = f.checkout("checkout");
        var results = race(() -> f.fines.pay("student", f.loan),
                () -> f.orders.act("user-1", "pay", "PAY", order));
        assertThat(results.stream().filter(o -> o.error == null).count()).isEqualTo(1);
        assertThat(results.stream().filter(o -> o.error != null).findFirst().orElseThrow().error)
                .hasMessage("WALLET_INSUFFICIENT_BALANCE");
        boolean finePaid = f.finePaid();
        assertThat(f.wallet.getBalance("user-1").balanceCents()).isEqualTo(finePaid ? 5000 : 2000);
        assertThat(f.state(order)).isEqualTo(finePaid ? "PENDING_PAYMENT" : "PAID");
        assertThat(f.wallet.getBalance("seller").pendingCents()).isEqualTo(finePaid ? 0 : 8000);
        assertThat(f.number("SELECT COUNT(*) FROM tblWalletEscrow")).isEqualTo(finePaid ? 0 : 1);
        assertJournal(f, 2);
    }

    @RepeatedTest(3)
    void fineAndRechargePreserveBothChanges() throws Exception {
        var f = new FineCommerceFixture();
        f.wallet.recharge("user-1", "fund", new BigDecimal("100"));
        var results = race(() -> f.fines.pay("student", f.loan),
                () -> f.wallet.recharge("user-1", "new-credit", new BigDecimal("200")));
        assertThat(results).allSatisfy(o -> assertThat(o.error).isNull());
        assertThat(f.wallet.getBalance("user-1").balanceCents()).isEqualTo(25000);
        assertThat(f.finePaid()).isTrue();
        assertJournal(f, 3);
    }

    @RepeatedTest(3)
    void fineAndShopRefundRemainConsistentRegardlessOfExecutionOrder() throws Exception {
        var f = new FineCommerceFixture();
        f.wallet.recharge("user-1", "fund", new BigDecimal("100"));
        var order = f.checkout("checkout");
        f.orders.act("user-1", "pay", "PAY", order);
        f.orders.act("user-1", "request", "REFUND_REQUEST", order);
        var results = race(() -> f.fines.pay("student", f.loan),
                () -> f.orders.act("seller", "approve", "REFUND_APPROVE", order));
        assertThat(results.get(1).error).isNull();
        if (results.getFirst().error != null) {
            assertThat(results.getFirst().error).hasMessage("WALLET_INSUFFICIENT_BALANCE");
            assertThat(f.finePaid()).isFalse();
            f.fines.pay("student", f.loan);
        }
        // Replays of both operations must not add a second credit or debit.
        f.fines.pay("student", f.loan);
        f.orders.act("seller", "approve", "REFUND_APPROVE", order);
        assertThat(f.wallet.getBalance("user-1").balanceCents()).isEqualTo(5000);
        assertThat(f.wallet.getBalance("seller").pendingCents()).isZero();
        assertThat(f.state(order)).isEqualTo("REFUNDED");
        assertJournal(f, 4);
    }

    @Test
    void duplicateFineRequestsAndShopPaymentOnlyChargeTheFineOnce() throws Exception {
        var f = new FineCommerceFixture();
        f.wallet.recharge("user-1", "fund", new BigDecimal("200"));
        var order = f.checkout("checkout");
        var results = race(() -> f.fines.pay("student", f.loan),
                () -> f.fines.pay("student", f.loan),
                () -> f.orders.act("user-1", "pay", "PAY", order));
        assertThat(results).allSatisfy(o -> assertThat(o.error).isNull());
        assertThat(results.get(0).value).isEqualTo(results.get(1).value);
        assertThat(f.wallet.getBalance("user-1").balanceCents()).isEqualTo(7000);
        assertThat(f.state(order)).isEqualTo("PAID");
        assertThat(f.number("SELECT COUNT(*) FROM tblWalletOperation WHERE operationType='LIBRARY_FINE'"))
                .isEqualTo(1);
        assertJournal(f, 3);
    }

    private static void assertJournal(FineCommerceFixture f, int operations) throws Exception {
        assertThat(f.number("SELECT COUNT(*) FROM tblWalletOperation")).isEqualTo(operations);
        assertThat(f.number("SELECT COUNT(*) FROM tblWalletEntry")).isEqualTo(operations * 2L);
        assertThat(f.number("SELECT SUM(deltaCents) FROM tblWalletEntry")).isZero();
        assertThat(f.number("SELECT SUM(deltaCents) FROM tblWalletEntry WHERE accountKind='USER' "
                + "AND accountKey='user-1'")).isEqualTo(f.wallet.getBalance("user-1").balanceCents());
    }

    @Test
    void failedFineTransactionRollsBackWhileShopPaymentCompletes() throws Exception {
        var f = new FineCommerceFixture();
        f.wallet.recharge("user-1", "fund", new BigDecimal("100"));
        var order = f.checkout("checkout");
        var delegate = new WalletFineService(Clock.systemUTC());
        var finePosted = new CountDownLatch(1);
        var faulty = new LibraryFineService(f.db.identities::get, f.db.loans,
                new AccessLibraryFineRepository(), f.db.transactions, new StripedResourceLockManager(),
                new WalletFinePort() {
                    @Override public WalletOperationResult pay(TransactionContext tx, String loan,
                            String user, long cents) throws SQLException {
                        delegate.pay(tx, loan, user, cents);
                        finePosted.countDown();
                        throw new IllegalStateException("injected-after-fine-posting");
                    }
                    @Override public WalletOperationResult receipt(TransactionContext tx, String loan,
                            String user, long cents) throws SQLException {
                        return delegate.receipt(tx, loan, user, cents);
                    }
                });
        var results = race(() -> faulty.pay("student", f.loan), () -> {
            assertThat(finePosted.await(10, TimeUnit.SECONDS)).isTrue();
            return f.orders.act("user-1", "pay", "PAY", order);
        });
        assertThat(results.getFirst().error).isNotNull();
        assertThat(results.getFirst().error).hasMessage("injected-after-fine-posting");
        assertThat(results.get(1).error).isNull();
        assertThat(f.finePaid()).isFalse();
        assertThat(f.wallet.getBalance("user-1").balanceCents()).isEqualTo(2000);
        assertThat(f.state(order)).isEqualTo("PAID");
        assertJournal(f, 2);
    }

    private record Outcome(Object value, RuntimeException error) { }

    private static List<Outcome> race(Callable<?>... actions) throws Exception {
        try (var pool = Executors.newFixedThreadPool(actions.length)) {
            var ready = new CountDownLatch(actions.length);
            var start = new CountDownLatch(1);
            List<Future<Outcome>> futures = new ArrayList<>();
            for (var action : actions) futures.add(pool.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Start timeout");
                try { return new Outcome(action.call(), null); }
                catch (RuntimeException error) { return new Outcome(null, error); }
            }));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Outcome> results = new ArrayList<>();
            for (var future : futures) results.add(future.get(30, TimeUnit.SECONDS));
            return results;
        }
    }
}
