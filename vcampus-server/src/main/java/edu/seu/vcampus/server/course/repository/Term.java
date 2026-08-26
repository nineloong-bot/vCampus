package edu.seu.vcampus.server.course.repository;

import java.time.Instant;
import java.time.LocalDate;

/** Persisted academic-term configuration and its enrollment windows. */
public record Term(String termId, String termCode, String termName, LocalDate startDate,
                   LocalDate endDate, Instant enrollmentStartAt, Instant enrollmentEndAt,
                   Instant adjustmentStartAt, Instant adjustmentEndAt, String termStatus,
                   long rowVersion, Instant createdAt, Instant updatedAt) {
}
