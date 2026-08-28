package edu.seu.vcampus.server.library.service;

import edu.seu.vcampus.common.library.BorrowBookCommand;
import edu.seu.vcampus.common.library.CopyStatus;
import edu.seu.vcampus.common.library.LoanView;
import edu.seu.vcampus.common.library.RenewLoanCommand;
import edu.seu.vcampus.common.library.ReturnBookCommand;
import edu.seu.vcampus.server.library.domain.Loan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReturnRenewTest {
    private LibraryServiceFixture fixture;
    private LibraryService service;

    @BeforeEach
    void setUp() throws Exception {
        fixture = new LibraryServiceFixture();
        fixture.seedCopies(2);
        fixture.addIdentity("token-1", "user-1", "STUDENT");
        fixture.addIdentity("token-2", "user-2", "STUDENT");
        service = fixture.service();
    }

    @Test
    void duplicateReturnDoesNotChangeCopyTwice() throws Exception {
        LoanView borrowed = service.borrow("token-1", new BorrowBookCommand("copy-1"));

        LoanView returned = service.returnBook("token-1",
                new ReturnBookCommand(borrowed.loanId(), 0));

        assertThat(returned.returnedAt()).isEqualTo(LibraryServiceFixture.NOW);
        assertThat(returned.rowVersion()).isEqualTo(1);
        try (Connection connection = fixture.connections.open()) {
            assertThat(fixture.books.requireCopy(connection, "copy-1").status())
                    .isEqualTo(CopyStatus.AVAILABLE);
        }
        assertThatThrownBy(() -> service.returnBook("token-1",
                new ReturnBookCommand(borrowed.loanId(), 1)))
                .isInstanceOf(LoanAlreadyReturnedException.class);
    }

    @Test
    void renewalUsesRolePolicyAndEnforcesLimit() {
        LoanView borrowed = service.borrow("token-1", new BorrowBookCommand("copy-1"));

        LoanView renewed = service.renew("token-1", new RenewLoanCommand(borrowed.loanId(), 0));

        assertThat(renewed.dueAt()).isEqualTo(borrowed.dueAt().plus(15, ChronoUnit.DAYS));
        assertThat(renewed.renewCount()).isEqualTo(1);
        assertThat(renewed.rowVersion()).isEqualTo(1);
        assertThatThrownBy(() -> service.renew("token-1",
                new RenewLoanCommand(borrowed.loanId(), 1)))
                .isInstanceOf(RenewalLimitReachedException.class);
    }

    @Test
    void overdueLoanCannotBeRenewed() throws Exception {
        Loan overdue = fixture.seedOverdue("copy-1", "user-1");

        assertThatThrownBy(() -> service.renew("token-1",
                new RenewLoanCommand(overdue.loanId(), 0)))
                .isInstanceOf(LoanOverdueException.class);
    }

    @Test
    void anotherUserCannotReturnOrRenewLoan() {
        LoanView borrowed = service.borrow("token-1", new BorrowBookCommand("copy-1"));

        assertThatThrownBy(() -> service.returnBook("token-2",
                new ReturnBookCommand(borrowed.loanId(), 0)))
                .isInstanceOf(LoanOwnershipException.class);
        assertThatThrownBy(() -> service.renew("token-2",
                new RenewLoanCommand(borrowed.loanId(), 0)))
                .isInstanceOf(LoanOwnershipException.class);
    }
}
