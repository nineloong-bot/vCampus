package edu.seu.vcampus.common.course;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/** Complete term configuration, curriculum mapping, and phase windows. */
public record TermView(String termId, String termCode, String termName,
                       LocalDate startDate, LocalDate endDate,
                       int academicYearStart, AcademicSeason season,
                       Instant enrollmentStartAt, Instant enrollmentEndAt,
                       Instant adjustmentStartAt, Instant adjustmentEndAt,
                       String termStatus, long rowVersion,
                       Instant createdAt, Instant updatedAt) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    public TermView {
        if (academicYearStart < 2000 || academicYearStart > 2200) {
            throw new IllegalArgumentException("invalid academic year");
        }
        Objects.requireNonNull(season, "season");
    }

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
