package edu.seu.vcampus.common.student;
import java.io.Serializable;
import java.time.LocalDate;
/**
 * Carries immutable change student status command data.
 * @param studentId the student identifier
 * @param status the status
 * @param effectiveDate the effective date
 * @param reason the reason
 * @param expectedVersion the expected version
 */
public record ChangeStudentStatusCommand(String studentId, StudentStatus status,
        LocalDate effectiveDate, String reason, long expectedVersion) implements Serializable { }
