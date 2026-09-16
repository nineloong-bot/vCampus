package edu.seu.vcampus.common.library;

import java.io.Serial;
import java.io.Serializable;

/** Administrative filtering and pagination for reservation records. */
public record AdminReservationSearchQuery(String keyword, ReservationStatus status,
        int page, int pageSize) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
