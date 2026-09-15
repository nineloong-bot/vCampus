package edu.seu.vcampus.server.wallet;

import edu.seu.vcampus.server.wallet.service.*;
import edu.seu.vcampus.server.persistence.*;
import edu.seu.vcampus.common.wallet.WalletHistoryQuery;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class WalletBoundaryTest {
    @Test void schemaRestartPreservesMoneyAndForeignKeys() throws Exception {
        try (var db = new WalletTestDatabase()) {
            var wallet = WalletServiceTest.service(db);
            wallet.recharge("buyer", "r", BigDecimal.ONE);
            var initializer = new WalletSchemaInitializer(java.nio.file.Path.of("../vcampus-database/schema/051_shop_wallet.sql"));
            initializer.initialize(db.connections());
            initializer.initialize(db.connections());
            assertThat(wallet.getBalance("buyer").balanceCents()).isEqualTo(100);
            assertThat(db.count("tblWalletOperation")).isEqualTo(1);
        }
    }

    @Test void refundAndSettlementRaceConservesMoneyAndJournal() throws Exception {
        try (var db = new WalletTestDatabase(); var pool = Executors.newFixedThreadPool(2)) {
            var wallet = WalletServiceTest.service(db);
            wallet.recharge("buyer", "r", new BigDecimal("10"));
            var posting = new WalletPostingService(Clock.systemUTC());
            new TransactionManager(db.connections()).inTransaction(c -> posting.post(new TransactionContext(c),
                    new WalletPosting("h", "o", "buyer", "seller", 1000, WalletPosting.Kind.HOLD)));
            var start = new CountDownLatch(1);
            var futures = new java.util.ArrayList<Future<Boolean>>();
            for (var kind : new WalletPosting.Kind[]{WalletPosting.Kind.REFUND, WalletPosting.Kind.SETTLE}) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        new TransactionManager(db.connections()).inTransaction(c -> posting.post(new TransactionContext(c),
                                new WalletPosting(kind.name(), "o", "buyer", "seller", 1000, kind)));
                        return true;
                    } catch (RuntimeException conflict) { return false; }
                }));
            }
            start.countDown(); int successes = 0;
            for (var f : futures) if (f.get(30, TimeUnit.SECONDS)) successes++;
            assertThat(successes).isEqualTo(1);
            assertThat(wallet.getBalance("buyer").balanceCents() + wallet.getBalance("seller").balanceCents()).isEqualTo(1000);
            assertThat(wallet.getBalance("seller").pendingCents()).isZero();
            try (var c = db.connections().open(); var s = c.createStatement();
                 var rows = s.executeQuery("SELECT SUM(deltaCents) FROM tblWalletEntry")) {
                rows.next(); assertThat(rows.getLong(1)).isZero();
            }
        }
    }
    @Test void historyIsPrivateBoundedAndImmutableReceiptsSurviveLaterChanges() throws Exception {
        try (var db = new WalletTestDatabase()) {
            var wallet = WalletServiceTest.service(db);
            var receipt = wallet.recharge("buyer", "r1", BigDecimal.ONE);
            wallet.recharge("buyer", "r2", BigDecimal.TEN);
            assertThat(wallet.recharge("buyer", "r1", BigDecimal.ONE)).isEqualTo(receipt);
            assertThat(wallet.getBalance("buyer").balanceCents()).isEqualTo(1100);
            var page = wallet.history("buyer", new WalletHistoryQuery(1, 1));
            assertThat(page.total()).isEqualTo(2); assertThat(page.items()).hasSize(1);
            var firstEntry = wallet.history("buyer", new WalletHistoryQuery(1, 10)).items().stream()
                    .filter(e -> e.operationId().equals(receipt.operationId())).findFirst().orElseThrow();
            assertThat(firstEntry.balanceCents()).isEqualTo(100);
            assertThat(firstEntry.orderKey()).isNull();
            assertThat(wallet.history("other", new WalletHistoryQuery(1, 10)).total()).isZero();
            assertThat(wallet.history("buyer", new WalletHistoryQuery(Integer.MAX_VALUE, 100)).items()).isEmpty();
            assertThatThrownBy(() -> wallet.history("buyer", new WalletHistoryQuery(1, 101))).isInstanceOf(WalletException.class);
            assertThatThrownBy(() -> wallet.recharge("buyer", " ", BigDecimal.ONE)).isInstanceOf(WalletException.class);
            assertThatThrownBy(() -> wallet.recharge("missing", "r", BigDecimal.ONE)).isInstanceOf(RuntimeException.class);
            assertThat(db.count("tblWalletOperation")).isEqualTo(2);
        }
    }
    @Test void storageCeilingAndAutoCommitCannotLeavePartialWrites() throws Exception {
        try (var db = new WalletTestDatabase()) {
            try (var c = db.connections().open(); var s = c.prepareStatement(
                    "INSERT INTO tblWalletAccount VALUES ('buyer',?,1)")) {
                s.setLong(1, WalletRules.MAX_CENTS); s.executeUpdate();
            }
            assertThatThrownBy(() -> WalletServiceTest.service(db).recharge("buyer", "over", BigDecimal.ONE))
                    .isInstanceOf(WalletException.class).hasMessage("WALLET_BALANCE_LIMIT");
            try (var c = db.connections().open()) {
                assertThatThrownBy(() -> new WalletPostingService(Clock.systemUTC()).post(new TransactionContext(c),
                        new WalletPosting("h", "o", "buyer", "seller", 1, WalletPosting.Kind.HOLD)))
                        .isInstanceOf(WalletException.class).hasMessage("WALLET_TRANSACTION_REQUIRED");
            }
            assertThat(db.count("tblWalletOperation")).isZero();
        }
    }
}
