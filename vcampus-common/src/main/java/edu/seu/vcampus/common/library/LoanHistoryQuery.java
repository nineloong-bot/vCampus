package edu.seu.vcampus.common.library;

import java.io.Serial;
import java.io.Serializable;

/** Paginates one borrower's loan history. */
/**
 * Carries immutable loan history query data.
 * @param status the status
 * @param page the page
 * @param pageSize the page size
 */
public record LoanHistoryQuery(LoanStatus status, int page, int pageSize)
        implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
