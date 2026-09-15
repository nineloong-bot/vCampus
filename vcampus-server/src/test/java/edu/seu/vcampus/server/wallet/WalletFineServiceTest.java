package edu.seu.vcampus.server.wallet;

import edu.seu.vcampus.common.wallet.WalletHistoryQuery;
import edu.seu.vcampus.server.persistence.TransactionContext;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.wallet.service.WalletException;
import edu.seu.vcampus.server.wallet.service.WalletFineService;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Clock;
import static org.assertj.core.api.Assertions.*;

class WalletFineServiceTest {
    @Test void fineDebitIsDurableBalancedAndDoesNotCreateShopEscrow() throws Exception {
        try (var db = new WalletTestDatabase()) {
            var wallet = WalletServiceTest.service(db);
            wallet.recharge("buyer", "recharge", new BigDecimal("100"));
            var tx = new TransactionManager(db.connections());
            var fines = new WalletFineService(Clock.systemUTC());
            var receipt = tx.inTransaction(c -> fines.pay(new TransactionContext(c), "loan-1", "buyer", 5050));
            var restarted = new WalletFineService(Clock.systemUTC());
            var replay = tx.inTransaction(c -> restarted.pay(new TransactionContext(c), "loan-1", "buyer", 5050));
            assertThat(replay).isEqualTo(receipt);
            assertThat(wallet.getBalance("buyer").balanceCents()).isEqualTo(4950);
            assertThat(db.count("tblWalletEscrow")).isZero();
            assertThat(db.count("tblWalletOperation")).isEqualTo(2);
            assertThat(db.count("tblWalletEntry")).isEqualTo(4);
            assertThat(wallet.history("buyer", new WalletHistoryQuery(1, 20)).items())
                    .anySatisfy(entry -> {
                        assertThat(entry.type()).isEqualTo("LIBRARY_FINE");
                        assertThat(entry.deltaCents()).isEqualTo(-5050);
                        assertThat(entry.orderKey()).isEqualTo("loan-1");
                    });
            try (var c = db.connections().open(); var s = c.createStatement();
                 var rows = s.executeQuery("SELECT SUM(deltaCents) FROM tblWalletEntry")) {
                rows.next(); assertThat(rows.getLong(1)).isZero();
            }
            assertThatThrownBy(() -> tx.inTransaction(c -> fines.pay(new TransactionContext(c), "loan-1", "buyer", 5051)))
                    .isInstanceOf(WalletException.class).hasMessage("WALLET_IDEMPOTENCY_CONFLICT");
        }
    }

    @Test void insufficientFundsAndTransactionFailureLeaveFineUnpaid() throws Exception {
        try (var db = new WalletTestDatabase()) {
            var wallet = WalletServiceTest.service(db);
            var tx = new TransactionManager(db.connections());
            var fines = new WalletFineService(Clock.systemUTC());
            assertThatThrownBy(() -> tx.inTransaction(c -> fines.pay(new TransactionContext(c), "loan", "buyer", 500)))
                    .hasMessage("WALLET_INSUFFICIENT_BALANCE");
            wallet.recharge("buyer", "recharge", BigDecimal.TEN);
            assertThatThrownBy(() -> tx.inTransaction(c -> {
                fines.pay(new TransactionContext(c), "loan", "buyer", 500);
                throw new IllegalStateException("rollback");
            })).hasMessage("rollback");
            assertThat(wallet.getBalance("buyer").balanceCents()).isEqualTo(1000);
            var unpaid = tx.inTransaction(c -> fines.receipt(new TransactionContext(c), "loan", "buyer", 500));
            assertThat(unpaid).isNull();
            assertThat(db.count("tblWalletOperation")).isEqualTo(1);
        }
    }
}
