package edu.seu.vcampus.server.library.domain;

import edu.seu.vcampus.common.library.ReservationStatus;

import java.time.Instant;

/** One reader's place in the reservation queue of a physical copy. */
public record BookReservation(String reservationId, String copyId, String bookId, String userId,
        String reserverRoleCode, Instant reservedAt, long queueOrder, ReservationStatus status,
        Instant readyAt, Instant expiresAt, long rowVersion) {
}
