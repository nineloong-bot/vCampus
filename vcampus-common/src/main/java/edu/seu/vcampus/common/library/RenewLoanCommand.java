package edu.seu.vcampus.common.library;

import java.io.Serial;
import java.io.Serializable;

/** Requests renewal of one active loan. */
public record RenewLoanCommand(String loanId, long expectedVersion) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
