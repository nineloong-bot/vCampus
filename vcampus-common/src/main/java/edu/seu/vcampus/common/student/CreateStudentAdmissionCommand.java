package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** Administrator input for atomically admitting one student. */
/**
 * Carries immutable create student admission command data.
 * @param studentName the student name
 * @param gender the gender
 * @param email the email
 * @param phone the phone
 * @param majorId the major identifier
 * @param classId the class identifier
 * @param enrollmentYear the enrollment year
 * @param studentType the student type
 */
public record CreateStudentAdmissionCommand(String studentName, String gender,
        String email, String phone, String majorId, String classId,
        int enrollmentYear, StudentType studentType) implements Serializable { }
