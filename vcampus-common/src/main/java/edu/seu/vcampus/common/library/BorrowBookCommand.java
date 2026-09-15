package edu.seu.vcampus.common.library;

import java.io.Serial;
import java.io.Serializable;

/** Requests a loan for one physical copy. */
/**
 * Carries immutable borrow book command data.
 * @param copyId the copy identifier
 */
public record BorrowBookCommand(String copyId) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
