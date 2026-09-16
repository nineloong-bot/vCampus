package edu.seu.vcampus.common.library;

import java.io.Serial;
import java.io.Serializable;

/** Lifecycle status of a book reservation. */
public enum ReservationStatus implements Serializable {
    /** Waiting in the copy queue; the copy is not yet held for this reader. */
    WAITING,
    /** The copy is held for this reader until the reservation expires. */
    READY,
    /** The reader borrowed the reserved copy. */
    FULFILLED,
    /** The held copy was not collected before the deadline. */
    EXPIRED,
    /** The reader or an administrator cancelled the reservation. */
    CANCELLED
}
