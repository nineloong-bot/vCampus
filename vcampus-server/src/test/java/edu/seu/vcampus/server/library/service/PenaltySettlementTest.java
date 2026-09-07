package edu.seu.vcampus.server.library.service;

import edu.seu.vcampus.common.library.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

class PenaltySettlementTest {
    @Test void readerCanDeclareMinorDamageAndReturnUsesCurrentConfiguredFine() throws Exception {
        var fixture = new LibraryServiceFixture(); fixture.seedCopies(1);
        fixture.addIdentity("student", "user-1", "STUDENT");
        var loan = fixture.seedOverdue("copy-1", "user-1"); var service = fixture.service();
        service.updatePolicy(new UpdateLibraryPolicyCommand("STUDENT", 5, 30, 1, 15, 0,
                new PenaltyPolicy(3, 10, new BigDecimal("1.20"), BigDecimal.TEN, BigDecimal.TEN,
                        new BigDecimal("5.50"), BigDecimal.TEN, BigDecimal.TEN)));
        var returned = service.returnBook("student", new ReturnBookCommand(loan.loanId(), 0, ReturnCondition.MINOR_DAMAGE));
        assertThat(returned.totalFine()).isEqualByComparingTo("6.70");
        try (var connection = fixture.connections.open()) {
            assertThat(fixture.books.requireCopy(connection, "copy-1").status()).isEqualTo(CopyStatus.DAMAGED);
            assertThat(fixture.loans.require(connection, loan.loanId()).damageFine()).isEqualByComparingTo("5.50");
        }
    }

    @Test void damagedOverdueReturnPersistsBothAmountsAndHistoricalAmountsSurvivePolicyChanges() throws Exception {
        var fixture = new LibraryServiceFixture(); fixture.seedCopies(1);
        fixture.addIdentity("student", "user-1", "STUDENT");
        var loan = fixture.seedOverdue("copy-1", "user-1");
        var service = fixture.service();
        var resolved = service.resolveLoan(new AdminResolveLoanCommand(loan.loanId(), LoanStatus.RETURNED, 0,
                ReturnCondition.MAJOR_DAMAGE));
        assertThat(resolved.overdueFine()).isEqualByComparingTo("0.50");
        assertThat(resolved.damageFine()).isEqualByComparingTo("50");
        assertThat(resolved.totalFine()).isEqualByComparingTo("50.50");
        try (var connection = fixture.connections.open()) {
            assertThat(fixture.books.requireCopy(connection, "copy-1").status()).isEqualTo(CopyStatus.DAMAGED);
        }
        service.updatePolicy(new UpdateLibraryPolicyCommand("STUDENT", 5, 30, 1, 15, 0,
                new PenaltyPolicy(1, 2, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN,
                        BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE)));
        var historical = service.getLoanHistory("student", new LoanHistoryQuery(null, 1, 20)).items().getFirst();
        assertThat(historical.totalFine()).isEqualByComparingTo("50.50");
        assertThat(historical.returnCondition()).isEqualTo(ReturnCondition.MAJOR_DAMAGE);
        assertThatThrownBy(() -> service.resolveLoan(new AdminResolveLoanCommand(loan.loanId(), LoanStatus.LOST, 1)))
                .isInstanceOf(LoanNotActiveException.class);
    }

    @Test void teacherLossUsesSavedTeacherPolicyAndStaleResolutionRollsBack() throws Exception {
        var fixture = new LibraryServiceFixture(); fixture.seedCopies(1);
        fixture.addIdentity("teacher", "user-2", "TEACHER");
        var service = fixture.service();
        service.updatePolicy(new UpdateLibraryPolicyCommand("TEACHER", 10, 60, 2, 30, 0,
                new PenaltyPolicy(3, 10, BigDecimal.ONE, new BigDecimal("2"), BigDecimal.TEN,
                        BigDecimal.ONE, BigDecimal.TEN, new BigDecimal("88.80"))));
        var loan = service.borrow("teacher", new BorrowBookCommand("copy-1"));
        assertThatThrownBy(() -> service.resolveLoan(new AdminResolveLoanCommand(loan.loanId(), LoanStatus.LOST, 9)))
                .isInstanceOf(java.util.ConcurrentModificationException.class);
        var resolved = service.resolveLoan(new AdminResolveLoanCommand(loan.loanId(), LoanStatus.LOST, 0));
        assertThat(resolved.totalFine()).isEqualByComparingTo("88.80");
        assertThat(service.getPolicies().stream().filter(p -> p.roleCode().equals("TEACHER")).findFirst().orElseThrow()
                .penalties().lostFine()).isEqualByComparingTo("88.80");
        try (var connection = fixture.connections.open()) {
            assertThat(fixture.books.requireCopy(connection, "copy-1").status()).isEqualTo(CopyStatus.LOST);
        }
    }

    @Test void invalidConditionDoesNotCloseLoan() throws Exception {
        var fixture = new LibraryServiceFixture(); fixture.seedCopies(1);
        fixture.addIdentity("student", "user-1", "STUDENT");
        var service = fixture.service(); var loan = service.borrow("student", new BorrowBookCommand("copy-1"));
        assertThatThrownBy(() -> service.resolveLoan(new AdminResolveLoanCommand(loan.loanId(), LoanStatus.RETURNED, 0,
                ReturnCondition.LOST))).isInstanceOf(IllegalArgumentException.class);
        assertThat(service.getCurrentLoans("student")).hasSize(1);
    }
}
