package edu.seu.vcampus.common.library;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/** Client-safe view of a reservation, its queue position, and its hold deadline. */
public record BookReservationView(String reservationId, String copyId, String bookId,
        String userId, String reserverLoginId, String bookTitle, String copyBarcode,
        ReservationStatus status, Instant reservedAt, Instant readyAt, Instant expiresAt,
        int queuePosition, long rowVersion) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public BookReservationView(String reservationId, String copyId, String bookId, String userId,
            ReservationStatus status, Instant reservedAt, Instant readyAt, Instant expiresAt,
            int queuePosition, long rowVersion) {
        this(reservationId, copyId, bookId, userId, null, null, null, status, reservedAt,
                readyAt, expiresAt, queuePosition, rowVersion);
    }

    /** True while the copy is physically held for this reader. */
    public boolean holdsCopy() {
        return status == ReservationStatus.READY;
    }
}
