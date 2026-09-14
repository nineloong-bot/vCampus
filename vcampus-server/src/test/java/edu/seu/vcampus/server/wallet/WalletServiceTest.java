package edu.seu.vcampus.server.wallet;

import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.persistence.TransactionContext;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.wallet.service.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Clock;
import static org.assertj.core.api.Assertions.*;

class WalletServiceTest {
    static WalletService service(WalletTestDatabase db) {
        return new WalletService(new TransactionManager(db.connections()), new StripedResourceLockManager(), Clock.systemUTC());
    }
    @Test void readIsPureAndRechargeIsExactAndDurablyIdempotent() throws Exception {
        try (var db = new WalletTestDatabase()) {
            var service = service(db);
            assertThat(service.getBalance("buyer").balanceCents()).isZero();
            assertThat(db.count("tblWalletAccount")).isZero();
            var first = service.recharge("buyer", "r1", new BigDecimal("100.01"));
            assertThat(first.balanceCents()).isEqualTo(10001);
            assertThat(service(db).recharge("buyer", "r1", new BigDecimal("100.01"))).isEqualTo(first);
            assertThat(db.count("tblWalletOperation")).isEqualTo(1);
            assertThat(db.count("tblWalletEntry")).isEqualTo(2);
            assertThatThrownBy(() -> service.recharge("buyer", "r1", new BigDecimal("200")))
                    .isInstanceOf(WalletException.class).hasMessage("WALLET_IDEMPOTENCY_CONFLICT");
            assertThat(service.getBalance("buyer").balanceCents()).isEqualTo(10001);
        }
    }
    @Test void invalidAmountsDoNotWriteAndLimitsAreInclusive() throws Exception {
        try (var db = new WalletTestDatabase()) {
            var service = service(db);
            for (String amount : new String[]{"0", "-1", "0.001", "1000.01", "1E+30"}) {
                assertThatThrownBy(() -> service.recharge("buyer", "bad", new BigDecimal(amount)))
                        .isInstanceOf(WalletException.class);
            }
            assertThatThrownBy(() -> service.recharge("buyer", "bad", null)).isInstanceOf(WalletException.class);
            assertThat(db.count("tblWalletOperation")).isZero();
            service.recharge("buyer", "min", new BigDecimal("0.01"));
            service.recharge("buyer", "max", new BigDecimal("1000"));
            assertThat(service.getBalance("buyer").balanceCents()).isEqualTo(100001);
        }
    }
    @Test void escrowSettlementAndRefundAreExclusiveAndTransactional() throws Exception {
        try (var db = new WalletTestDatabase()) {
            var service = service(db);
            service.recharge("buyer", "r", new BigDecimal("100"));
            var tx = new TransactionManager(db.connections());
            var posting = new WalletPostingService(Clock.systemUTC());
            var hold = new WalletPosting("hold-1", "order-1", "buyer", "seller", 4000, WalletPosting.Kind.HOLD);
            tx.inTransaction(c -> posting.post(new TransactionContext(c), hold));
            assertThat(service.getBalance("buyer").balanceCents()).isEqualTo(6000);
            assertThat(service.getBalance("seller").pendingCents()).isEqualTo(4000);
            var settle = new WalletPosting("settle-1", "order-1", "buyer", "seller", 4000, WalletPosting.Kind.SETTLE);
            tx.inTransaction(c -> posting.post(new TransactionContext(c), settle));
            tx.inTransaction(c -> posting.post(new TransactionContext(c), settle));
            assertThat(service.getBalance("seller").balanceCents()).isEqualTo(4000);
            assertThat(service.getBalance("seller").pendingCents()).isZero();
            assertThatThrownBy(() -> tx.inTransaction(c -> posting.post(new TransactionContext(c),
                    new WalletPosting("refund-1", "order-1", "buyer", "seller", 4000, WalletPosting.Kind.REFUND))))
                    .isInstanceOf(WalletException.class);
            long operations = db.count("tblWalletOperation");
            assertThatThrownBy(() -> tx.inTransaction(c -> {
                posting.post(new TransactionContext(c), new WalletPosting("hold-2", "order-2", "buyer", "seller", 1000, WalletPosting.Kind.HOLD));
                throw new IllegalStateException("injected failure");
            })).isInstanceOf(IllegalStateException.class);
            assertThat(service.getBalance("buyer").balanceCents()).isEqualTo(6000);
            assertThat(db.count("tblWalletOperation")).isEqualTo(operations);
        }
    }
}
