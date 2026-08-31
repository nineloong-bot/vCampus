package edu.seu.vcampus.common.library;

import java.io.Serial;
import java.io.Serializable;

/** Administrator resolution of one active borrowing record. */
public record AdminResolveLoanCommand(String loanId, LoanStatus resolution,
        long expectedVersion) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
}
