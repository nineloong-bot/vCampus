package edu.seu.vcampus.server.library.service;

/** Raised when a reader already holds an open reservation for the same copy. */
public final class DuplicateReservationException extends RuntimeException {
    public DuplicateReservationException(String userId, String copyId) {
        super("Reservation already open for " + userId + " on " + copyId);
    }
}
