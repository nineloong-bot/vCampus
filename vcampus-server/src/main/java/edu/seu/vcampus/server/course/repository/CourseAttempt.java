package edu.seu.vcampus.server.course.repository;

import java.time.Instant;

/** Imported passed-or-failed course outcome used to determine retake eligibility. */
public record CourseAttempt(String attemptId, String studentId, String courseId, String termId,
                            String outcome, String sourceReference, Instant importedAt) {
}
