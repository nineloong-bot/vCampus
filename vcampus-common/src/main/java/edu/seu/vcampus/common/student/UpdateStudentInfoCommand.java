package edu.seu.vcampus.common.student;
import java.io.Serializable;
import java.time.LocalDate;
/**
 * Carries immutable update student info command data.
 * @param studentId the student identifier
 * @param studentNumber the student number
 * @param classId the class identifier
 * @param status the status
 * @param effectiveDate the effective date
 * @param reason the reason
 * @param expectedVersion the expected version
 */
public record UpdateStudentInfoCommand(String studentId, String studentNumber, String classId,
        StudentStatus status, LocalDate effectiveDate, String reason, long expectedVersion)
        implements Serializable { }
