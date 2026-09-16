package edu.seu.vcampus.server.library.repository;

import edu.seu.vcampus.common.library.AdminReservationSearchQuery;
import edu.seu.vcampus.common.library.BookReservationView;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.server.library.domain.BookReservation;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

/** Persists reservation queues and their read views. */
public interface ReservationRepository {
    BookReservation insert(Connection connection, BookReservation reservation) throws SQLException;

    BookReservation require(Connection connection, String reservationId) throws SQLException;

    void update(Connection connection, BookReservation reservation, long expectedVersion)
            throws SQLException;

    /** Open reservations ({@code WAITING} or {@code READY}) for one copy, earliest first. */
    List<BookReservation> findOpenForCopy(Connection connection, String copyId) throws SQLException;

    boolean hasOpenForUserAndCopy(Connection connection, String copyId, String userId)
            throws SQLException;

    int countOpenForUser(Connection connection, String userId) throws SQLException;

    long nextQueueOrder(Connection connection, String copyId) throws SQLException;

    /** One-based place of a queue entry among the still-open reservations of its copy. */
    int queuePosition(Connection connection, String copyId, long queueOrder) throws SQLException;

    /** Marks every held reservation past its deadline as expired and returns the updated rows. */
    List<BookReservation> markExpiredReady(Connection connection, Instant now) throws SQLException;

    List<BookReservationView> findForUser(Connection connection, String userId, Instant now)
            throws SQLException;

    PageResult<BookReservationView> searchAll(Connection connection,
            AdminReservationSearchQuery query, Instant now) throws SQLException;
}
