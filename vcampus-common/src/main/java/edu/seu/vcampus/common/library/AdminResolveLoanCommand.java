package edu.seu.vcampus.common.library;

import java.io.Serial;
import java.io.Serializable;

/** Administrator resolution of one active borrowing record. */
/**
 * Carries immutable admin resolve loan command data.
 * @param loanId the loan identifier
 * @param resolution the resolution
 * @param expectedVersion the expected version
 * @param condition the condition
 */
public record AdminResolveLoanCommand(String loanId, LoanStatus resolution,
        long expectedVersion, ReturnCondition condition) implements Serializable {
    /**
     * Validates and creates a admin resolve loan command.
     * @param loanId the loan identifier
     * @param resolution the resolution
     * @param expectedVersion the expected version
     */
    public AdminResolveLoanCommand(String loanId, LoanStatus resolution, long expectedVersion) {
        this(loanId, resolution, expectedVersion,
                resolution == LoanStatus.LOST ? ReturnCondition.LOST : ReturnCondition.NORMAL);
    }

    @Serial private static final long serialVersionUID = 1L;
}
