package edu.seu.vcampus.common.library;

import java.io.Serial;
import java.io.Serializable;

/** Administrative filtering and pagination for all loan records. */
public record AdminLoanSearchQuery(String borrowerUserId, LoanStatus status,
        int page, int pageSize) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
