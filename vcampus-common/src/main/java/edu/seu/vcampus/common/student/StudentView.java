package edu.seu.vcampus.common.student;

import java.io.Serializable;
import java.time.LocalDate;

/** Complete student profile returned to authorized clients. */
/**
 * Carries immutable student view data.
 * @param studentId the student identifier
 * @param userId the user identifier
 * @param campusCardNumber the campus card number
 * @param studentNumber the student number
 * @param studentType the student type
 * @param studentName the student name
 * @param gender the gender
 * @param email the email
 * @param phone the phone
 * @param majorId the major identifier
 * @param classId the class identifier
 * @param enrollmentDate the enrollment date
 * @param status the status
 * @param rowVersion the row version
 * @param departmentName the department name
 * @param majorName the major name
 * @param className the class name
 */
public record StudentView(String studentId, String userId, String campusCardNumber,
        String studentNumber, StudentType studentType, String studentName, String gender,
        String email, String phone, String majorId, String classId,
        LocalDate enrollmentDate, StudentStatus status, long rowVersion,
        String departmentName, String majorName, String className)
        implements Serializable { }
