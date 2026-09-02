package edu.seu.vcampus.common.library;

import java.io.Serial;
import java.io.Serializable;

/** Adds one barcoded physical copy to a catalog title. */
public record AddBookCopyCommand(String bookId, String barcode, String locationCode)
        implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
