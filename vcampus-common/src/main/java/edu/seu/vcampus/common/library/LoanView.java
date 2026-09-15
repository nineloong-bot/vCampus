package edu.seu.vcampus.common.library;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/** Safe loan information returned to a client. */
/**
 * Carries immutable loan view data.
 * @param loanId the loan identifier
 * @param copyId the copy identifier
 * @param bookId the book identifier
 * @param borrowerUserId the borrower user identifier
 * @param borrowedAt the borrowed at
 * @param dueAt the due at
 * @param returnedAt the returned at
 * @param renewCount the renew count
 * @param status the status
 * @param rowVersion the row version
 * @param borrowerLoginId the borrower login identifier
 * @param bookTitle the book title
 * @param copyBarcode the copy barcode
 * @param overdueFine the overdue fine
 * @param damageFine the damage fine
 * @param returnCondition the return condition
 */
public record LoanView(String loanId, String copyId, String bookId, String borrowerUserId,
        Instant borrowedAt, Instant dueAt, Instant returnedAt, int renewCount,
        LoanStatus status, long rowVersion, String borrowerLoginId, String bookTitle,
        String copyBarcode, java.math.BigDecimal overdueFine, java.math.BigDecimal damageFine,
        ReturnCondition returnCondition) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Validates and creates a loan view.
     * @param loanId the loan identifier
     * @param copyId the copy identifier
     * @param bookId the book identifier
     * @param borrowerUserId the borrower user identifier
     * @param borrowedAt the borrowed at
     * @param dueAt the due at
     * @param returnedAt the returned at
     * @param renewCount the renew count
     * @param status the status
     * @param rowVersion the row version
     */
    public LoanView(String loanId, String copyId, String bookId, String borrowerUserId,
            Instant borrowedAt, Instant dueAt, Instant returnedAt, int renewCount,
            LoanStatus status, long rowVersion) {
        this(loanId, copyId, bookId, borrowerUserId, borrowedAt, dueAt, returnedAt,
                renewCount, status, rowVersion, null, null, null);
    }

    /**
     * Validates and creates a loan view.
     * @param loanId the loan id
     * @param copyId the copy id
     * @param bookId the book id
     * @param borrowerUserId the borrower user id
     * @param borrowedAt the borrowed at
     * @param dueAt the due at
     * @param returnedAt the returned at
     * @param renewCount the renew count
     * @param status the status
     * @param rowVersion the row version
     * @param borrowerLoginId the borrower login id
     * @param bookTitle the book title
     * @param copyBarcode the copy barcode
     */
    public LoanView(String loanId, String copyId, String bookId, String borrowerUserId,
            Instant borrowedAt, Instant dueAt, Instant returnedAt, int renewCount,
            LoanStatus status, long rowVersion, String borrowerLoginId, String bookTitle, String copyBarcode) {
        this(loanId, copyId, bookId, borrowerUserId, borrowedAt, dueAt, returnedAt, renewCount,
                status, rowVersion, borrowerLoginId, bookTitle, copyBarcode,
                java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, ReturnCondition.NORMAL);
    }

    /**
     * Returns the assessed amount for future payment-module integration.
     * @return combined overdue and damage fine
     */
    public java.math.BigDecimal totalFine() { return overdueFine.add(damageFine); }

    /**
     * Returns the display loan number result.
     * @return the computed result
     */
    public String displayLoanNumber() {
        String compact = loanId.replace("-", "").toUpperCase(java.util.Locale.ROOT);
        return "BR-" + compact.substring(0, Math.min(8, compact.length()));
    }
}
