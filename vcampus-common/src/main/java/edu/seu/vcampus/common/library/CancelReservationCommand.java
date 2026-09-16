package edu.seu.vcampus.common.library;

import java.io.Serial;
import java.io.Serializable;

/** Cancels the caller's own reservation. */
public record CancelReservationCommand(String reservationId, long expectedVersion)
        implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
