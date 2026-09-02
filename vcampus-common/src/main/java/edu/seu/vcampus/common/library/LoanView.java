package edu.seu.vcampus.common.library;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/** Safe loan information returned to a client. */
public record LoanView(String loanId, String copyId, String bookId, String borrowerUserId,
        Instant borrowedAt, Instant dueAt, Instant returnedAt, int renewCount,
        LoanStatus status, long rowVersion, String borrowerLoginId, String bookTitle,
        String copyBarcode) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public LoanView(String loanId, String copyId, String bookId, String borrowerUserId,
            Instant borrowedAt, Instant dueAt, Instant returnedAt, int renewCount,
            LoanStatus status, long rowVersion) {
        this(loanId, copyId, bookId, borrowerUserId, borrowedAt, dueAt, returnedAt,
                renewCount, status, rowVersion, null, null, null);
    }

    public String displayLoanNumber() {
        String compact = loanId.replace("-", "").toUpperCase(java.util.Locale.ROOT);
        return "BR-" + compact.substring(0, Math.min(8, compact.length()));
    }
}
