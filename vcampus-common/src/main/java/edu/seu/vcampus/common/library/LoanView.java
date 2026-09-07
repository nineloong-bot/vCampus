package edu.seu.vcampus.common.library;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/** Safe loan information returned to a client. */
public record LoanView(String loanId, String copyId, String bookId, String borrowerUserId,
        Instant borrowedAt, Instant dueAt, Instant returnedAt, int renewCount,
        LoanStatus status, long rowVersion, String borrowerLoginId, String bookTitle,
        String copyBarcode, java.math.BigDecimal overdueFine, java.math.BigDecimal damageFine,
        ReturnCondition returnCondition) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public LoanView(String loanId, String copyId, String bookId, String borrowerUserId,
            Instant borrowedAt, Instant dueAt, Instant returnedAt, int renewCount,
            LoanStatus status, long rowVersion) {
        this(loanId, copyId, bookId, borrowerUserId, borrowedAt, dueAt, returnedAt,
                renewCount, status, rowVersion, null, null, null);
    }

    public LoanView(String loanId, String copyId, String bookId, String borrowerUserId,
            Instant borrowedAt, Instant dueAt, Instant returnedAt, int renewCount,
            LoanStatus status, long rowVersion, String borrowerLoginId, String bookTitle, String copyBarcode) {
        this(loanId, copyId, bookId, borrowerUserId, borrowedAt, dueAt, returnedAt, renewCount,
                status, rowVersion, borrowerLoginId, bookTitle, copyBarcode,
                java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, ReturnCondition.NORMAL);
    }

    /** Assessed amount for future payment-module integration; no debit is performed here. */
    public java.math.BigDecimal totalFine() { return overdueFine.add(damageFine); }

    public String displayLoanNumber() {
        String compact = loanId.replace("-", "").toUpperCase(java.util.Locale.ROOT);
        return "BR-" + compact.substring(0, Math.min(8, compact.length()));
    }
}
