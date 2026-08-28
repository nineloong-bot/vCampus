package edu.seu.vcampus.server.library.service;

import edu.seu.vcampus.common.library.BorrowBookCommand;
import edu.seu.vcampus.common.library.CopyStatus;
import edu.seu.vcampus.common.library.LoanView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrentBorrowTest {
    private LibraryServiceFixture fixture;

    @BeforeEach
    void setUp() throws Exception {
        fixture = new LibraryServiceFixture();
    }

    @Test
    void oneOfTwentyBorrowersGetsTheCopy() throws Exception {
        fixture.seedCopies(1);
        LibraryService service = fixture.service();
        List<Callable<LoanView>> attempts = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            String token = "token-" + index;
            fixture.addIdentity(token, "user-" + index, "STUDENT");
            attempts.add(() -> service.borrow(token, new BorrowBookCommand("copy-1")));
        }

        List<Outcome> outcomes = runTogether(attempts);

        assertThat(outcomes).filteredOn(Outcome::success).hasSize(1);
        try (Connection connection = fixture.connections.open()) {
            assertThat(fixture.books.requireCopy(connection, "copy-1").status())
                    .isEqualTo(CopyStatus.BORROWED);
        }
        assertThat(fixture.loanCountForCopy("copy-1")).isEqualTo(1);
    }

    @Test
    void sameUserCannotRacePastStudentLimit() throws Exception {
        fixture.seedCopies(6);
        LibraryService service = fixture.service();
        fixture.addIdentity("same-user", "user-1", "STUDENT");
        List<Callable<LoanView>> attempts = new ArrayList<>();
        for (int index = 1; index <= 6; index++) {
            String copyId = "copy-" + index;
            attempts.add(() -> service.borrow("same-user", new BorrowBookCommand(copyId)));
        }

        List<Outcome> outcomes = runTogether(attempts);

        assertThat(outcomes).filteredOn(Outcome::success).hasSize(5);
        assertThat(outcomes).filteredOn(outcome -> outcome.error() instanceof LoanLimitReachedException)
                .hasSize(1);
    }

    private static List<Outcome> runTogether(List<Callable<LoanView>> attempts) throws Exception {
        CountDownLatch ready = new CountDownLatch(attempts.size());
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(attempts.size())) {
            var futures = attempts.stream().map(attempt -> executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    return new Outcome(attempt.call(), null);
                } catch (Throwable error) {
                    return new Outcome(null, error);
                }
            })).toList();
            ready.await();
            start.countDown();
            List<Outcome> outcomes = new ArrayList<>();
            for (var future : futures) {
                outcomes.add(future.get());
            }
            return outcomes;
        }
    }

    private record Outcome(LoanView value, Throwable error) {
        boolean success() {
            return error == null;
        }
    }
}
