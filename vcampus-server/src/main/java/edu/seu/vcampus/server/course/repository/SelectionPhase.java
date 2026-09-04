package edu.seu.vcampus.server.course.repository;

import java.time.Instant;

/** Persisted manually controlled course-selection phase. */
public record SelectionPhase(String phaseId, String termId, String phaseType, String displayTitle,
                             String phaseStatus, long rowVersion, Instant createdAt, Instant updatedAt) {
}
