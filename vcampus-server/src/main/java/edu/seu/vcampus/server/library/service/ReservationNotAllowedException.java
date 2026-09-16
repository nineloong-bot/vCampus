package edu.seu.vcampus.server.library.service;

/** Raised when a copy cannot be reserved in its current state. */
public final class ReservationNotAllowedException extends RuntimeException {
    public ReservationNotAllowedException(String copyId) {
        super("Copy cannot be reserved: " + copyId);
    }
}
