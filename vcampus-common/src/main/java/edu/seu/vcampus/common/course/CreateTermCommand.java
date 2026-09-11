package edu.seu.vcampus.common.course;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

/** Creates a complete academic-term window and curriculum mapping. */
public record CreateTermCommand(String termCode, String termName,
                                LocalDate startDate, LocalDate endDate,
                                int academicYearStart, AcademicSeason season,
                                Instant enrollmentStartAt, Instant enrollmentEndAt,
                                Instant adjustmentStartAt, Instant adjustmentEndAt,
                                String termStatus) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
    private static final Set<String> STATUSES = Set.of("PLANNED", "ACTIVE", "CLOSED");

    public CreateTermCommand {
        Objects.requireNonNull(termCode); Objects.requireNonNull(termName);
        Objects.requireNonNull(startDate); Objects.requireNonNull(endDate);
        Objects.requireNonNull(season); Objects.requireNonNull(enrollmentStartAt);
        Objects.requireNonNull(enrollmentEndAt); Objects.requireNonNull(adjustmentStartAt);
        Objects.requireNonNull(adjustmentEndAt); Objects.requireNonNull(termStatus);
        CourseValidation.text("termCode", termCode, 24);
        CourseValidation.text("termName", termName, 64);
        if (academicYearStart < 2000 || academicYearStart > 2200
                || !endDate.isAfter(startDate)
                || !enrollmentEndAt.isAfter(enrollmentStartAt)
                || !adjustmentStartAt.isAfter(enrollmentEndAt)
                || !adjustmentEndAt.isAfter(adjustmentStartAt)
                || !STATUSES.contains(termStatus)) throw new IllegalArgumentException("invalid term");
    }

    public CreateTermCommand(String termCode, String termName, LocalDate startDate,
                             LocalDate endDate, Instant enrollmentStartAt,
                             Instant enrollmentEndAt, Instant adjustmentStartAt,
                             Instant adjustmentEndAt, String termStatus) {
        this(termCode, termName, startDate, endDate, Math.max(2000, startDate.getYear()),
                AcademicSeason.fromStartMonth(startDate.getMonthValue()), enrollmentStartAt,
                enrollmentEndAt, adjustmentStartAt, adjustmentEndAt, termStatus);
    }
}
