package edu.seu.vcampus.server.wallet;

import edu.seu.vcampus.server.wallet.service.*;
import edu.seu.vcampus.server.persistence.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.math.BigDecimal;
import java.time.Clock;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.*;

/** Opt-in generator: creates 1000 real wallet operations in an isolated Access fixture. */
@EnabledIfSystemProperty(named = "wallet.demo.generate", matches = "true")
class WalletDemoDatasetTest {
    @Test void generatesExactly1000OperationsAnd2000BalancedEntries() throws Exception {
        Path directory = Path.of("target/wallet-demo");
        Files.createDirectories(directory);
        Path output = Files.createTempDirectory(directory, "dataset-");
        try (var db = new WalletTestDatabase()) {
            var wallet = WalletServiceTest.service(db);
            var transactions = new TransactionManager(db.connections());
            var posting = new WalletPostingService(Clock.systemUTC());
            for (int i = 0; i < 250; i++) {
                wallet.recharge("buyer", "demo-a-" + i, new BigDecimal("100"));
                wallet.recharge("buyer", "demo-b-" + i, new BigDecimal("200"));
                String order = "demo-order-" + i;
                var terminal = i % 2 == 0 ? WalletPosting.Kind.REFUND : WalletPosting.Kind.SETTLE;
                transactions.inTransaction(c -> {
                    posting.post(new TransactionContext(c), new WalletPosting("hold-" + order, order,
                            "buyer", "seller", 3000, WalletPosting.Kind.HOLD));
                    return posting.post(new TransactionContext(c), new WalletPosting("finish-" + order, order,
                            "buyer", "seller", 3000, terminal));
                });
            }
            assertThat(db.count("tblWalletOperation")).isEqualTo(1000);
            assertThat(db.count("tblWalletEntry")).isEqualTo(2000);
            assertThat(db.count("tblWalletEscrow")).isEqualTo(250);
            assertThat(wallet.getBalance("buyer").balanceCents()).isEqualTo(7_125_000);
            assertThat(wallet.getBalance("seller").balanceCents()).isEqualTo(375_000);
            assertThat(wallet.getBalance("seller").pendingCents()).isZero();
            try (var c = db.connections().open(); var s = c.createStatement()) {
                try (var rows = s.executeQuery("SELECT SUM(deltaCents) FROM tblWalletEntry")) {
                    rows.next(); assertThat(rows.getLong(1)).isZero();
                }
                try (var rows = s.executeQuery("SELECT operationType,COUNT(*) FROM tblWalletOperation GROUP BY operationType")) {
                    var counts = new java.util.HashMap<String, Integer>();
                    while (rows.next()) counts.put(rows.getString(1), rows.getInt(2));
                    assertThat(counts).containsExactlyInAnyOrderEntriesOf(java.util.Map.of(
                            "RECHARGE", 500, "PAYMENT", 250, "REFUND", 125, "INCOME", 125));
                }
                var csv = new StringBuilder("operationId,type,amountCents,balanceAfter,orderKey\n");
                try (var rows = s.executeQuery("SELECT * FROM tblWalletOperation ORDER BY createdAt,operationId")) {
                    while (rows.next()) csv.append(rows.getString("operationId")).append(',')
                            .append(rows.getString("operationType")).append(',').append(rows.getLong("amountCents"))
                            .append(',').append(rows.getLong("balanceAfter")).append(',')
                            .append(rows.getString("orderKey")).append('\n');
                }
                Files.writeString(output.resolve("operations.csv"), csv);
            }
            Files.copy(db.path, output.resolve("wallet-demo.accdb"));
            Files.writeString(output.resolve("manifest.json"), """
                    {"operations":1000,"entries":2000,"escrows":250,
                     "recharge":500,"payment":250,"refund":125,"income":125,
                     "buyerBalanceCents":7125000,"sellerBalanceCents":375000,
                     "scope":"isolated wallet fixture; not a production campus database"}
                    """);
            // Verify the persisted copy, not only the driver's live in-memory view.
            try (var c = java.sql.DriverManager.getConnection("jdbc:ucanaccess://" + output.resolve("wallet-demo.accdb").toAbsolutePath());
                 var s = c.createStatement(); var rows = s.executeQuery("SELECT COUNT(*) FROM tblWalletOperation")) {
                rows.next(); assertThat(rows.getLong(1)).isEqualTo(1000);
            }
            ConnectionProvider recovered = () -> java.sql.DriverManager.getConnection(
                    "jdbc:ucanaccess://" + output.resolve("wallet-demo.accdb").toAbsolutePath());
            var restored = new WalletService(new TransactionManager(recovered),
                    new edu.seu.vcampus.server.concurrency.StripedResourceLockManager(), Clock.systemUTC());
            assertThat(restored.recharge("buyer", "demo-a-0", new BigDecimal("100")).balanceCents()).isEqualTo(10000);
            assertThat(restored.getBalance("buyer").balanceCents()).isEqualTo(7_125_000);
            try (var c = recovered.open(); var s = c.createStatement();
                 var rows = s.executeQuery("SELECT COUNT(*) FROM tblWalletOperation")) {
                rows.next(); assertThat(rows.getLong(1)).isEqualTo(1000);
            }
            System.out.println("WALLET_DATASET=" + output.toAbsolutePath());
        }
    }
}
