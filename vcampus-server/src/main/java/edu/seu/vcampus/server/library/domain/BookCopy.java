package edu.seu.vcampus.server.library.domain;

import edu.seu.vcampus.common.library.CopyStatus;

/** One barcoded physical copy of a catalog book. */
public record BookCopy(String copyId, String bookId, String barcode, String locationCode,
        CopyStatus status, long rowVersion) {
}
