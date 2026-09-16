package edu.seu.vcampus.common.library;

import java.io.Serial;
import java.io.Serializable;

/** Client-safe view of a physical library copy. */
public record BookCopyView(String copyId, String bookId, String barcode, String locationCode,
        CopyStatus status, long rowVersion, String reservationId, String reservedForLoginId)
        implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public BookCopyView(String copyId, String bookId, String barcode, String locationCode,
            CopyStatus status, long rowVersion) {
        this(copyId, bookId, barcode, locationCode, status, rowVersion, null, null);
    }

    /** Label shown in the copy status column, including the holder's card number. */
    public String statusLabel() {
        if (status == CopyStatus.RESERVED && reservedForLoginId != null && !reservedForLoginId.isBlank()) {
            return "已预约：" + reservedForLoginId;
        }
        return null;
    }
}
