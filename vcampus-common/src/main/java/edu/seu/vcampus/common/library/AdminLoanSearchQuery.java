package edu.seu.vcampus.common.library;

import java.io.Serial;
import java.io.Serializable;

/** Administrative filtering and pagination for all loan records. */
/**
 * Carries immutable admin loan search query data.
 * @param borrowerUserId the borrower user identifier
 * @param status the status
 * @param page the page
 * @param pageSize the page size
 */
public record AdminLoanSearchQuery(String borrowerUserId, LoanStatus status,
        int page, int pageSize) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
