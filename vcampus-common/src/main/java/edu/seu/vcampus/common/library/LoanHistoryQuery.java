package edu.seu.vcampus.common.library;

import java.io.Serial;
import java.io.Serializable;

/** Paginates one borrower's loan history. */
public record LoanHistoryQuery(LoanStatus status, int page, int pageSize)
        implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
