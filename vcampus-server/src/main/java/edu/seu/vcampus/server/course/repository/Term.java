package edu.seu.vcampus.server.course.repository;

import edu.seu.vcampus.common.course.AcademicSeason;

import java.time.Instant;
import java.time.LocalDate;

/** Persisted academic-term configuration and its enrollment windows. */
public record Term(String termId, String termCode, String termName, LocalDate startDate,
                   LocalDate endDate, int academicYearStart, AcademicSeason season,
                   Instant enrollmentStartAt, Instant enrollmentEndAt,
                   Instant adjustmentStartAt, Instant adjustmentEndAt, String termStatus,
                   long rowVersion, Instant createdAt, Instant updatedAt) {
    public Term(String termId, String termCode, String termName, LocalDate startDate,
                LocalDate endDate, Instant enrollmentStartAt, Instant enrollmentEndAt,
                Instant adjustmentStartAt, Instant adjustmentEndAt, String termStatus,
                long rowVersion, Instant createdAt, Instant updatedAt) {
        this(termId, termCode, termName, startDate, endDate,
                Math.max(2000, startDate.getYear()), AcademicSeason.fromStartMonth(startDate.getMonthValue()),
                enrollmentStartAt, enrollmentEndAt, adjustmentStartAt, adjustmentEndAt,
                termStatus, rowVersion, createdAt, updatedAt);
    }
}
