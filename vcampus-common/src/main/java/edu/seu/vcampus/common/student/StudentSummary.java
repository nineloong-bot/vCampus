package edu.seu.vcampus.common.student;
import java.io.Serializable;
/**
 * Carries immutable student summary data.
 * @param studentId the student identifier
 * @param campusCardNumber the campus card number
 * @param studentNumber the student number
 * @param studentName the student name
 * @param majorId the major identifier
 * @param classId the class identifier
 * @param status the status
 */
public record StudentSummary(String studentId, String campusCardNumber, String studentNumber,
        String studentName, String majorId, String classId, StudentStatus status)
        implements Serializable { }
