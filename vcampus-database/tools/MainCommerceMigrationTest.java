import java.nio.file.Files;
import java.nio.file.Path;

/** Copy-only regression runner for the supplied main and commerce snapshots. */
public final class MainCommerceMigrationTest {
    /** Runs preservation, source completeness, and overwrite prevention checks. */
    public static void main(String[] args) throws Exception {
        Path main = Path.of(args[0]);
        Path source = Path.of(args[1]);
        Path directory = Files.createTempDirectory("commerce-migration-test-");
        Path baseline = directory.resolve("baseline.accdb");
        Files.copy(main, baseline);
        boolean rejected = false;
        try {
            CommerceDataChecks.verify(main, source, baseline);
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        if (!rejected) {
            throw new AssertionError("Unmigrated main must fail source record completeness");
        }
        Path output = directory.resolve("candidate.accdb");
        MainCommerceMigration.main(new String[]{"--source-commerce", main.toString(),
                source.toString(), output.toString()});
        CommerceDataChecks.verify(main, source, output);
        rejected = false;
        try {
            MainCommerceMigration.main(new String[]{"--source-commerce", main.toString(),
                    source.toString(), output.toString()});
        } catch (java.nio.file.FileAlreadyExistsException expected) {
            rejected = true;
        }
        if (!rejected) {
            throw new AssertionError("Existing destination must never be overwritten");
        }
        Path brokenWallet = directory.resolve("broken-wallet.accdb");
        Files.copy(output, brokenWallet);
        try (var db = CommerceDataChecks.open(brokenWallet, false)) {
            var table = db.getTable("tblWalletAccount");
            var row = table.iterator().next();
            row.put("balanceCents", ((Number) row.get("balanceCents")).longValue() + 1);
            table.updateRow(row);
        }
        expectFailure(() -> {
            try (var db = CommerceDataChecks.open(brokenWallet, true)) {
                CommerceIntegrity.validate(db);
            }
        }, "Ledger must reject a one-cent balance corruption");
        Path brokenUser = directory.resolve("broken-user.accdb");
        Files.copy(output, brokenUser);
        try (var db = CommerceDataChecks.open(brokenUser, false)) {
            var table = db.getTable("tblUser");
            var row = table.iterator().next();
            row.put("loginId", "migration-test-identity-mismatch");
            table.updateRow(row);
        }
        expectFailure(() -> CommerceDataChecks.verify(main, source, brokenUser),
                "Nonshop account mutations must be rejected");
        expectFailure(() -> {
            try (var original = CommerceDataChecks.open(main, true);
                    var changed = CommerceDataChecks.open(brokenUser, true)) {
                CommerceDataChecks.accounts(original, changed);
            }
        }, "Changed login identity must be rejected before migration");
        Path invalidPeer = directory.resolve("invalid-peer.accdb");
        Files.copy(output, invalidPeer);
        try (var db = CommerceDataChecks.open(invalidPeer, false)) {
            var table = db.getTable("tblWalletOperation");
            var row = table.iterator().next();
            row.put("peerId", "LIBRARY");
            table.updateRow(row);
        }
        expectFailure(() -> {
            try (var db = CommerceDataChecks.open(invalidPeer, true)) {
                CommerceIntegrity.validate(db);
            }
        }, "LIBRARY sentinel on recharge must be rejected");
        CommerceDataChecks.verifyNoncommerce(main, output);
        expectFailure(() -> CommerceDataChecks.verifyNoncommerce(main, brokenUser),
                "Startup validation must reject noncommerce changes");
        System.out.println("PASS: preservation, overwrite, wallet/user tamper, identity, sentinel, startup checks: "
                + directory);
    }
    private static void expectFailure(CheckedAction action, String message) throws Exception {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private interface CheckedAction {
        void run() throws Exception;
    }}
