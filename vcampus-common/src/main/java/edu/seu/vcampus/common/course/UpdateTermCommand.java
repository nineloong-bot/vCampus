package edu.seu.vcampus.common.course;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/** Optimistically updates a complete academic-term configuration. */
public record UpdateTermCommand(String termId, String termCode, String termName,
                                LocalDate startDate, LocalDate endDate,
                                int academicYearStart, AcademicSeason season,
                                Instant enrollmentStartAt, Instant enrollmentEndAt,
                                Instant adjustmentStartAt, Instant adjustmentEndAt,
                                String termStatus, long expectedVersion) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    public UpdateTermCommand {
        Objects.requireNonNull(termId);
        new CreateTermCommand(termCode, termName, startDate, endDate, academicYearStart, season,
                enrollmentStartAt, enrollmentEndAt, adjustmentStartAt, adjustmentEndAt, termStatus);
        CourseValidation.text("termId", termId, 36);
        if (expectedVersion < 0) throw new IllegalArgumentException("invalid term");
    }

    public UpdateTermCommand(String termId, String termCode, String termName,
                             LocalDate startDate, LocalDate endDate,
                             Instant enrollmentStartAt, Instant enrollmentEndAt,
                             Instant adjustmentStartAt, Instant adjustmentEndAt,
                             String termStatus, long expectedVersion) {
        this(termId, termCode, termName, startDate, endDate, Math.max(2000, startDate.getYear()),
                AcademicSeason.fromStartMonth(startDate.getMonthValue()), enrollmentStartAt,
                enrollmentEndAt, adjustmentStartAt, adjustmentEndAt, termStatus, expectedVersion);
    }
}
