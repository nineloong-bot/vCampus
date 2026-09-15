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
    /**
     * Creates a term with its required collaborators.
     * @param termId the term identifier
     * @param termCode the term code
     * @param termName the term name
     * @param startDate the start date
     * @param endDate the end date
     * @param enrollmentStartAt the enrollment start at
     * @param enrollmentEndAt the enrollment end at
     * @param adjustmentStartAt the adjustment start at
     * @param adjustmentEndAt the adjustment end at
     * @param termStatus the term status
     * @param rowVersion the row version
     * @param createdAt the created at
     * @param updatedAt the updated at
     */
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
