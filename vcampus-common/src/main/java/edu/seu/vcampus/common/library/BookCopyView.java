package edu.seu.vcampus.common.library;

import java.io.Serial;
import java.io.Serializable;

/** Client-safe view of a physical library copy. */
/**
 * Carries immutable book copy view data.
 * @param copyId the copy identifier
 * @param bookId the book identifier
 * @param barcode the barcode
 * @param locationCode the location code
 * @param status the status
 * @param rowVersion the row version
 */
public record BookCopyView(String copyId, String bookId, String barcode, String locationCode,
        CopyStatus status, long rowVersion) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
