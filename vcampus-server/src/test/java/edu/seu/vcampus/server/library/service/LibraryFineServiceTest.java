package edu.seu.vcampus.server.library.service;

import edu.seu.vcampus.common.library.*;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.library.repository.AccessLibraryFineRepository;
import edu.seu.vcampus.server.wallet.service.WalletFineService;
import edu.seu.vcampus.server.wallet.service.WalletService;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.concurrent.Executors;
import static org.assertj.core.api.Assertions.*;

class LibraryFineServiceTest {
    private final LibraryServiceFixture fixture;
    private final StripedResourceLockManager locks = new StripedResourceLockManager();
    private final LibraryFineService fines;
    private final WalletService wallet;

    LibraryFineServiceTest() throws Exception {
        fixture = new LibraryServiceFixture();
        fixture.seedCopies(3);
        fixture.addIdentity("student", "user-1", "STUDENT");
        fixture.addIdentity("other", "user-2", "STUDENT");
        try (var c = fixture.connections.open(); var s = c.createStatement()) {
            s.execute("INSERT INTO tblRole VALUES ('STUDENT','Student')");
            for (String id : new String[]{"user-1", "user-2"}) {
                s.execute("INSERT INTO tblUser (userId,loginId,passwordHash,passwordSalt,passwordIterations,roleCode,"
                        + "accountStatus,mustChangePassword,createdAt,updatedAt) VALUES ('" + id + "','" + id
                        + "','test','test',1,'STUDENT','ACTIVE',FALSE,#2026-08-01#,#2026-08-01#)");
            }
            for (String sql : Files.readString(Path.of("../vcampus-database/schema/051_shop_wallet.sql")).split(";")) {
                if (!sql.isBlank()) s.execute(sql);
            }
        }
        fines = new LibraryFineService(fixture.identities::get, fixture.loans, new AccessLibraryFineRepository(),
                fixture.transactions, locks, new WalletFineService(Clock.systemUTC()));
        wallet = new WalletService(fixture.transactions, locks, Clock.systemUTC());
    }

    @Test void assessedDamageFineRemainsDueUntilOwnerPaysAndRetriesOnlyDebitOnce() throws Exception {
        var loan = fixture.seedOverdue("copy-1", "user-1");
        fixture.service().resolveLoan(new AdminResolveLoanCommand(loan.loanId(), LoanStatus.RETURNED, 0,
                ReturnCondition.MAJOR_DAMAGE));
        assertThat(fines.mine("student", new LibraryFineQuery(1, 20)).items().getFirst().paid()).isFalse();
        assertThatThrownBy(() -> fines.pay("student", loan.loanId())).hasMessage("WALLET_INSUFFICIENT_BALANCE");
        wallet.recharge("user-1", "credit", new BigDecimal("100"));
        assertThatThrownBy(() -> fines.pay("other", loan.loanId())).isInstanceOf(LoanOwnershipException.class);
        var paid = fines.pay("student", loan.loanId());
        assertThat(paid.balanceCents()).isEqualTo(4950);
        assertThat(fines.pay("student", loan.loanId())).isEqualTo(paid);
        assertThat(fines.mine("student", new LibraryFineQuery(1, 20)).items().getFirst().paid()).isTrue();
        assertThat(fines.mine("other", new LibraryFineQuery(1, 20)).total()).isZero();
        assertThat(fines.all(new LibraryFineQuery(1, 20)).items().getFirst().paymentId()).isEqualTo(paid.operationId());
    }

    @Test void lostFineIsPayableButActiveAndZeroFineLoansAreNot() throws Exception {
        var active = fixture.service().borrow("student", new BorrowBookCommand("copy-1"));
        assertThatThrownBy(() -> fines.pay("student", active.loanId())).isInstanceOf(IllegalArgumentException.class);
        fixture.service().returnBook("student", new ReturnBookCommand(active.loanId(), 0));
        assertThatThrownBy(() -> fines.pay("student", active.loanId())).isInstanceOf(IllegalArgumentException.class);
        var lost = fixture.seedOverdue("copy-2", "user-1");
        fixture.service().resolveLoan(new AdminResolveLoanCommand(lost.loanId(), LoanStatus.LOST, 0, ReturnCondition.LOST));
        wallet.recharge("user-1", "credit", new BigDecimal("200"));
        assertThat(fines.pay("student", lost.loanId()).balanceCents()).isEqualTo(9950);
        assertThat(fines.mine("student", new LibraryFineQuery(1, 1)).total()).isEqualTo(1);
    }

    @Test void simultaneousPaymentsShareTheWalletLockAndCannotDoubleDebit() throws Exception {
        var loan = fixture.seedOverdue("copy-1", "user-1");
        fixture.service().resolveLoan(new AdminResolveLoanCommand(loan.loanId(), LoanStatus.RETURNED, 0));
        wallet.recharge("user-1", "credit", BigDecimal.TEN);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> fines.pay("student", loan.loanId()));
            var second = pool.submit(() -> fines.pay("student", loan.loanId()));
            assertThat(first.get()).isEqualTo(second.get());
        }
        assertThat(wallet.getBalance("user-1").balanceCents()).isEqualTo(950);
    }

    @Test void paginatedQueriesKeepBorrowersIsolatedAndRejectInvalidPages() throws Exception {
        for (int i = 1; i <= 3; i++) {
            var loan = fixture.seedOverdue("copy-" + i, i == 2 ? "user-2" : "user-1");
            fixture.service().resolveLoan(new AdminResolveLoanCommand(loan.loanId(), LoanStatus.RETURNED, 0));
        }
        var first = fines.mine("student", new LibraryFineQuery(1, 1));
        var second = fines.mine("student", new LibraryFineQuery(2, 1));
        assertThat(first.total()).isEqualTo(2);
        assertThat(second.items()).hasSize(1);
        assertThat(first.items().getFirst().loan().loanId()).isNotEqualTo(second.items().getFirst().loan().loanId());
        assertThat(fines.all(new LibraryFineQuery(1, 20)).total()).isEqualTo(3);
        assertThatThrownBy(() -> fines.mine("student", new LibraryFineQuery(0, 20)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
