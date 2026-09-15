package edu.seu.vcampus.common.library;

import java.io.Serial;
import java.io.Serializable;

/** Page of finalized, nonzero library fines; borrower scope is set by the server. */
public record LibraryFineQuery(int page, int pageSize) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
}
