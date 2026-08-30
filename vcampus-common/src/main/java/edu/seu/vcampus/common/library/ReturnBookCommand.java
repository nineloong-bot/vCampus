package edu.seu.vcampus.common.library;

import java.io.Serial;
import java.io.Serializable;

/** Returns a loan using optimistic version checking. */
public record ReturnBookCommand(String loanId, long expectedVersion) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
