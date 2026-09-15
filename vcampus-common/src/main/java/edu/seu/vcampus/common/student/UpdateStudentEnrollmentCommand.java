package edu.seu.vcampus.common.student;
import java.io.Serializable;
import java.time.LocalDate;
/**
 * Carries immutable update student enrollment command data.
 * @param studentId the student identifier
 * @param classId the class identifier
 * @param effectiveDate the effective date
 * @param reason the reason
 * @param expectedVersion the expected version
 */
public record UpdateStudentEnrollmentCommand(String studentId, String classId,
        LocalDate effectiveDate, String reason, long expectedVersion) implements Serializable { }
