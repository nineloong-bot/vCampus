package edu.seu.vcampus.common.library;

import java.io.Serial;
import java.io.Serializable;

/** Requests renewal of one active loan. */
/**
 * Carries immutable renew loan command data.
 * @param loanId the loan identifier
 * @param expectedVersion the expected version
 */
public record RenewLoanCommand(String loanId, long expectedVersion) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
