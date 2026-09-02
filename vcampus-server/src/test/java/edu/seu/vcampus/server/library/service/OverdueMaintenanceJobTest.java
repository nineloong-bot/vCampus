package edu.seu.vcampus.server.library.service;

import edu.seu.vcampus.common.library.LoanStatus;
import edu.seu.vcampus.server.library.domain.Loan;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.Clock;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class OverdueMaintenanceJobTest {
    @Test
    void marksOnlyNewlyOverdueLoansAndIsIdempotent() throws Exception {
        LibraryServiceFixture fixture = new LibraryServiceFixture();
        fixture.seedCopies(1);
        Loan overdue = fixture.seedOverdue("copy-1", "user-1");
        OverdueMaintenanceJob job = new OverdueMaintenanceJob(fixture.loans,
                fixture.transactions, Clock.fixed(LibraryServiceFixture.NOW, ZoneOffset.UTC));

        assertThat(job.runOnce()).isEqualTo(1);
        assertThat(job.runOnce()).isZero();
        try (Connection connection = fixture.connections.open()) {
            Loan refreshed = fixture.loans.require(connection, overdue.loanId());
            assertThat(refreshed.status()).isEqualTo(LoanStatus.OVERDUE);
            assertThat(refreshed.rowVersion()).isEqualTo(1);
        }
    }
}
