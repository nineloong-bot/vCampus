package edu.seu.vcampus.server.library.service;

import edu.seu.vcampus.common.library.CopyStatus;
import edu.seu.vcampus.common.library.ReservationStatus;
import edu.seu.vcampus.server.library.domain.BookCopy;
import edu.seu.vcampus.server.library.domain.BookReservation;
import edu.seu.vcampus.server.library.repository.BookRepository;
import edu.seu.vcampus.server.library.repository.LibraryPolicyRepository;
import edu.seu.vcampus.server.library.repository.ReservationRepository;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Applies reservation queue rules inside an existing transaction: expiry releases held copies
 * and the earliest waiting reader is promoted whenever a copy becomes free.
 */
final class ReservationQueueService {
    static final int FALLBACK_RESERVE_DAYS = 3;

    private final BookRepository books;
    private final ReservationRepository reservations;
    private final LibraryPolicyRepository policies;

    ReservationQueueService(BookRepository books, ReservationRepository reservations,
            LibraryPolicyRepository policies) {
        this.books = books;
        this.reservations = reservations;
        this.policies = policies;
    }

    /** Expires held reservations past their deadline and hands each freed copy to the next reader. */
    void refreshExpired(Connection connection, Instant now) throws SQLException {
        List<BookReservation> expired = reservations.markExpiredReady(connection, now);
        if (expired.isEmpty()) return;
        Set<String> copies = new LinkedHashSet<>();
        for (BookReservation reservation : expired) copies.add(reservation.copyId());
        for (String copyId : copies) {
            promoteNextReader(connection, copyId, now);
        }
    }

    /**
     * Holds a free copy for the earliest waiting reader, or releases it when the queue is empty.
     * Borrowed, damaged, and lost copies are left untouched.
     */
    void promoteNextReader(Connection connection, String copyId, Instant now) throws SQLException {
        BookCopy copy = books.requireCopy(connection, copyId);
        if (copy.status() != CopyStatus.AVAILABLE && copy.status() != CopyStatus.RESERVED) return;
        List<BookReservation> queue = reservations.findOpenForCopy(connection, copyId);
        if (queue.isEmpty()) {
            if (copy.status() == CopyStatus.RESERVED) {
                books.updateCopyStatus(connection, copyId, CopyStatus.AVAILABLE, copy.rowVersion());
            }
            return;
        }
        BookReservation head = queue.get(0);
        if (head.status() == ReservationStatus.READY) {
            if (copy.status() != CopyStatus.RESERVED) {
                books.updateCopyStatus(connection, copyId, CopyStatus.RESERVED, copy.rowVersion());
            }
            return;
        }
        BookReservation ready = new BookReservation(head.reservationId(), head.copyId(), head.bookId(),
                head.userId(), head.reserverRoleCode(), head.reservedAt(), head.queueOrder(),
                ReservationStatus.READY, now, now.plus(reserveDays(connection, head.reserverRoleCode()),
                        ChronoUnit.DAYS), head.rowVersion() + 1);
        reservations.update(connection, ready, head.rowVersion());
        if (copy.status() != CopyStatus.RESERVED) {
            books.updateCopyStatus(connection, copyId, CopyStatus.RESERVED, copy.rowVersion());
        }
    }

    /** Returns the reader holding the copy, or {@code null} when no held reservation exists. */
    BookReservation readyHolder(Connection connection, String copyId) throws SQLException {
        for (BookReservation reservation : reservations.findOpenForCopy(connection, copyId)) {
            if (reservation.status() == ReservationStatus.READY) return reservation;
        }
        return null;
    }

    int reserveDays(Connection connection, String roleCode) throws SQLException {
        try {
            return policies.require(connection, roleCode).reserveDays();
        } catch (NoSuchElementException missing) {
            return FALLBACK_RESERVE_DAYS;
        }
    }
}
