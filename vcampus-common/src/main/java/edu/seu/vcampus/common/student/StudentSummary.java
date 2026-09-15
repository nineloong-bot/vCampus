package edu.seu.vcampus.common.student;

import java.io.Serializable;

/**
 * Summary view of a student record for list and search displays.
 *
 * @param studentId unique student identifier
 * @param campusCardNumber campus card number
 * @param studentNumber student number
 * @param studentName student full name
 * @param majorId associated major identifier
 * @param classId associated class identifier
 * @param status current student status
 * @param className optional human-readable class name
 */
public record StudentSummary(String studentId, String campusCardNumber, String studentNumber,
        String studentName, String majorId, String classId, StudentStatus status,
        String className)
        implements Serializable {

    /**
     * Constructs a summary without an explicit class name.
     *
     * @param studentId unique student identifier
     * @param campusCardNumber campus card number
     * @param studentNumber student number
     * @param studentName student full name
     * @param majorId associated major identifier
     * @param classId associated class identifier
     * @param status current student status
     */
    public StudentSummary(String studentId, String campusCardNumber, String studentNumber,
            String studentName, String majorId, String classId, StudentStatus status) {
        this(studentId, campusCardNumber, studentNumber, studentName, majorId, classId, status, null);
    }
}
