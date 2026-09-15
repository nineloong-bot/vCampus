package edu.seu.vcampus.common.library;

import java.io.Serial;
import java.io.Serializable;

/** Returns a loan using optimistic version checking. */
/**
 * Carries immutable return book command data.
 * @param loanId the loan identifier
 * @param expectedVersion the expected version
 * @param condition the condition
 */
public record ReturnBookCommand(String loanId, long expectedVersion, ReturnCondition condition) implements Serializable {
    /**
     * Validates and creates a return book command.
     * @param loanId the loan identifier
     * @param expectedVersion the expected version
     */
    public ReturnBookCommand(String loanId, long expectedVersion) {
        this(loanId, expectedVersion, ReturnCondition.NORMAL);
    }

    @Serial
    private static final long serialVersionUID = 1L;
}
