package edu.seu.vcampus.common.library;

import java.io.Serial;
import java.io.Serializable;

/** Client-safe view of a physical library copy. */
public record BookCopyView(String copyId, String bookId, String barcode, String locationCode,
        CopyStatus status, long rowVersion) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
