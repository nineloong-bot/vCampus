package edu.seu.vcampus.common.library;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/** Safe loan information returned to a client. */
public record LoanView(String loanId, String copyId, String bookId, String borrowerUserId,
        Instant borrowedAt, Instant dueAt, Instant returnedAt, int renewCount,
        LoanStatus status, long rowVersion) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
