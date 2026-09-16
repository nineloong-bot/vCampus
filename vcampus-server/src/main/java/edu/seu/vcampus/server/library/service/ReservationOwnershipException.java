package edu.seu.vcampus.server.library.service;

/** Raised when a reader tries to cancel someone else's reservation. */
public final class ReservationOwnershipException extends RuntimeException {
    public ReservationOwnershipException(String reservationId) {
        super("Reservation belongs to another reader: " + reservationId);
    }
}
