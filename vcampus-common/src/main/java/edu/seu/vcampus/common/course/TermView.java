package edu.seu.vcampus.common.course;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/** Complete term configuration, curriculum mapping, and phase windows. */
/**
 * Carries immutable term view data.
 * @param termId the term identifier
 * @param termCode the term code
 * @param termName the term name
 * @param startDate the start date
 * @param endDate the end date
 * @param academicYearStart the academic year start
 * @param season the season
 * @param enrollmentStartAt the enrollment start at
 * @param enrollmentEndAt the enrollment end at
 * @param adjustmentStartAt the adjustment start at
 * @param adjustmentEndAt the adjustment end at
 * @param termStatus the term status
 * @param rowVersion the row version
 * @param createdAt the created at
 * @param updatedAt the updated at
 */
public record TermView(String termId, String termCode, String termName,
                       LocalDate startDate, LocalDate endDate,
                       int academicYearStart, AcademicSeason season,
                       Instant enrollmentStartAt, Instant enrollmentEndAt,
                       Instant adjustmentStartAt, Instant adjustmentEndAt,
                       String termStatus, long rowVersion,
                       Instant createdAt, Instant updatedAt) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    /**
     * Validates and creates a term view.
     * @param termId the term id
     * @param termCode the term code
     * @param termName the term name
     * @param startDate the start date
     * @param endDate the end date
     * @param academicYearStart the academic year start
     * @param season the season
     * @param enrollmentStartAt the enrollment start at
     * @param enrollmentEndAt the enrollment end at
     * @param adjustmentStartAt the adjustment start at
     * @param adjustmentEndAt the adjustment end at
     * @param termStatus the term status
     * @param rowVersion the row version
     * @param createdAt the created at
     * @param updatedAt the updated at
     */
    public TermView {
        if (academicYearStart < 2000 || academicYearStart > 2200) {
            throw new IllegalArgumentException("invalid academic year");
        }
        Objects.requireNonNull(season, "season");
    }

    /**
     * Validates and creates a term view.
     * @param termId the term id
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
    public TermView(String termId, String termCode, String termName,
                    LocalDate startDate, LocalDate endDate,
                    Instant enrollmentStartAt, Instant enrollmentEndAt,
                    Instant adjustmentStartAt, Instant adjustmentEndAt,
                    String termStatus, long rowVersion, Instant createdAt, Instant updatedAt) {
        this(termId, termCode, termName, startDate, endDate, Math.max(2000, startDate.getYear()),
                AcademicSeason.fromStartMonth(startDate.getMonthValue()),
                enrollmentStartAt, enrollmentEndAt, adjustmentStartAt, adjustmentEndAt,
                termStatus, rowVersion, createdAt, updatedAt);
    }
}
