package edu.seu.vcampus.server.student.service;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.Optional;

/** Narrow calendar query used to decide whether freshman admission remains open. */
public interface AutumnTermCalendarPort {
    /** Returns the configured autumn-term start date for an enrollment year. */
    Optional<LocalDate> findAutumnStartDate(Connection connection, int enrollmentYear);
}
