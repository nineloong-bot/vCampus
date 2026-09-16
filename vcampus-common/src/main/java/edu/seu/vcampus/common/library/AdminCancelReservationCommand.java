package edu.seu.vcampus.common.library;

import java.io.Serial;
import java.io.Serializable;

/** Administrator cancellation of any reservation. */
public record AdminCancelReservationCommand(String reservationId, long expectedVersion)
        implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
