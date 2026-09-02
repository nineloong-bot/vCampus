package edu.seu.vcampus.common.library;

import java.io.Serial;
import java.io.Serializable;

/** Requests a loan for one physical copy. */
public record BorrowBookCommand(String copyId) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
