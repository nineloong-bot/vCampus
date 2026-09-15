package edu.seu.vcampus.common.student;
import java.io.Serializable;
/**
 * Carries immutable student identity data.
 * @param studentId the student identifier
 * @param userId the user identifier
 * @param campusCardNumber the campus card number
 * @param studentNumber the student number
 * @param studentType the student type
 * @param majorId the major identifier
 * @param classId the class identifier
 * @param status the status
 */
public record StudentIdentity(String studentId, String userId, String campusCardNumber,
        String studentNumber, StudentType studentType, String majorId, String classId,
        StudentStatus status) implements Serializable { }
