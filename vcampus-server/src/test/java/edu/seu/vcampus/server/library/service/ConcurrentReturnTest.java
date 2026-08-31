package edu.seu.vcampus.server.library.service;

import edu.seu.vcampus.common.library.BorrowBookCommand;
import edu.seu.vcampus.common.library.AdminResolveLoanCommand;
import edu.seu.vcampus.common.library.CopyStatus;
import edu.seu.vcampus.common.library.LoanHistoryQuery;
import edu.seu.vcampus.common.library.LoanStatus;
import edu.seu.vcampus.common.library.LoanView;
import edu.seu.vcampus.common.library.ReturnBookCommand;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrentReturnTest {
    @Test
    void onlyOneOfTwoDifferentReturnRequestsSucceeds() throws Exception {
        LibraryServiceFixture fixture = new LibraryServiceFixture();
        fixture.seedCopies(1);
        fixture.addIdentity("token", "user-1", "STUDENT");
        LibraryService service = fixture.service();
        LoanView borrowed = service.borrow("token", new BorrowBookCommand("copy-1"));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<Outcome> outcomes = new ArrayList<>();
        var executor = Executors.newFixedThreadPool(2);
        try {
            var calls = List.of(
                    executor.submit(() -> attempt(service, borrowed, ready, start)),
                    executor.submit(() -> attempt(service, borrowed, ready, start)));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (var call : calls) {
                outcomes.add(call.get(10, TimeUnit.SECONDS));
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(outcomes).filteredOn(Outcome::success).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> outcome.error() instanceof LoanAlreadyReturnedException
                || outcome.error() instanceof java.util.ConcurrentModificationException).hasSize(1);
    }

    @Test
    void userReturnAndAdministratorLostResolutionCannotSplitLoanAndCopyState() throws Exception {
        LibraryServiceFixture fixture = new LibraryServiceFixture();
        fixture.seedCopies(1); fixture.addIdentity("token", "user-1", "STUDENT");
        LibraryService service = fixture.service();
        LoanView borrowed = service.borrow("token", new BorrowBookCommand("copy-1"));
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);

        List<Outcome> outcomes = new ArrayList<>();
        var executor = Executors.newFixedThreadPool(2);
        try {
            var user = executor.submit(() -> attempt(service, borrowed, ready, start));
            var admin = executor.submit(() -> attemptAdminLost(service, borrowed, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            outcomes.add(user.get(10, TimeUnit.SECONDS));
            outcomes.add(admin.get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        assertThat(outcomes).filteredOn(Outcome::success).hasSize(1);
        LoanStatus finalLoan = service.getLoanHistory("token", new LoanHistoryQuery(null, 1, 20))
                .items().getFirst().status();
        CopyStatus finalCopy = service.getBook("book-1").copies().getFirst().status();
        assertThat((finalLoan == LoanStatus.RETURNED && finalCopy == CopyStatus.AVAILABLE)
                || (finalLoan == LoanStatus.LOST && finalCopy == CopyStatus.LOST)).isTrue();
    }

    private static Outcome attempt(LibraryService service, LoanView borrowed,
            CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            return new Outcome(service.returnBook("token",
                    new ReturnBookCommand(borrowed.loanId(), 0)), null);
        } catch (Throwable error) {
            return new Outcome(null, error);
        }
    }

    private static Outcome attemptAdminLost(LibraryService service, LoanView borrowed,
            CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown(); start.await();
        try {
            return new Outcome(service.resolveLoan(new AdminResolveLoanCommand(
                    borrowed.loanId(), LoanStatus.LOST, borrowed.rowVersion())), null);
        } catch (Throwable error) { return new Outcome(null, error); }
    }

    private record Outcome(LoanView value, Throwable error) {
        boolean success() {
            return error == null;
        }
    }
}
