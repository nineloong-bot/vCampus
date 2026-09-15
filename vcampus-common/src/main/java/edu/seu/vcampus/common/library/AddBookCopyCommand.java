package edu.seu.vcampus.common.library;

import java.io.Serial;
import java.io.Serializable;

/** Adds one barcoded physical copy to a catalog title. */
/**
 * Carries immutable add book copy command data.
 * @param bookId the book identifier
 * @param barcode the barcode
 * @param locationCode the location code
 */
public record AddBookCopyCommand(String bookId, String barcode, String locationCode)
        implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
