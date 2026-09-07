package edu.seu.vcampus.common.library;

import java.io.Serial;
import java.io.Serializable;

/** Returns a loan using optimistic version checking. */
public record ReturnBookCommand(String loanId, long expectedVersion, ReturnCondition condition) implements Serializable {
    public ReturnBookCommand(String loanId, long expectedVersion) {
        this(loanId, expectedVersion, ReturnCondition.NORMAL);
    }

    @Serial
    private static final long serialVersionUID = 1L;
}
