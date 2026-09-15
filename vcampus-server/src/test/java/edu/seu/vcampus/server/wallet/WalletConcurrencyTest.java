package edu.seu.vcampus.server.wallet;

import edu.seu.vcampus.server.wallet.service.*;
import edu.seu.vcampus.server.persistence.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class WalletConcurrencyTest {
    @Test void concurrentDuplicateRechargeCreditsOnceAcrossServiceInstances() throws Exception {
        try (var db = new WalletTestDatabase(); var pool = Executors.newFixedThreadPool(8)) {
            var service = WalletServiceTest.service(db);
            var start = new CountDownLatch(1);
            var futures = new java.util.ArrayList<Future<Boolean>>();
            for (int i = 0; i < 8; i++) futures.add(pool.submit(() -> {
                start.await();
                try { WalletServiceTest.service(db).recharge("buyer", "same", new BigDecimal("100")); return true; }
                catch (RuntimeException conflict) { return false; }
            }));
            start.countDown();
            int successes = 0;
            for (var future : futures) if (future.get(30, TimeUnit.SECONDS)) successes++;
            assertThat(successes).isPositive();
            service.recharge("buyer", "same", new BigDecimal("100"));
            assertThat(service.getBalance("buyer").balanceCents()).isEqualTo(10000);
            assertThat(db.count("tblWalletOperation")).isEqualTo(1);
            assertThat(db.count("tblWalletEntry")).isEqualTo(2);
        }
    }
    @Test void twoOrdersCannotSpendTheSameLastBalance() throws Exception {
        try (var db = new WalletTestDatabase(); var pool = Executors.newFixedThreadPool(2)) {
            var service = WalletServiceTest.service(db);
            service.recharge("buyer", "fund", new BigDecimal("100"));
            var start = new CountDownLatch(1);
            var futures = new java.util.ArrayList<Future<Boolean>>();
            for (int i = 0; i < 2; i++) {
                String id = "order-" + i;
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        new TransactionManager(db.connections()).inTransaction(c ->
                            new WalletPostingService(Clock.systemUTC()).post(new TransactionContext(c),
                                new WalletPosting(id, id, "buyer", "seller", 8000, WalletPosting.Kind.HOLD)));
                        return true;
                    } catch (RuntimeException conflict) { return false; }
                }));
            }
            start.countDown(); int succeeded = 0;
            for (var future : futures) if (future.get(30, TimeUnit.SECONDS)) succeeded++;
            assertThat(succeeded).isEqualTo(1);
            assertThat(service.getBalance("buyer").balanceCents()).isEqualTo(2000);
            assertThat(service.getBalance("seller").pendingCents()).isEqualTo(8000);
        }
    }
}
