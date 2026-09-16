package edu.seu.vcampus.server.library.service;

/** Raised when a reservation is no longer waiting or held. */
public final class ReservationNotActiveException extends RuntimeException {
    public ReservationNotActiveException(String reservationId) {
        super("Reservation is not active: " + reservationId);
    }
}
