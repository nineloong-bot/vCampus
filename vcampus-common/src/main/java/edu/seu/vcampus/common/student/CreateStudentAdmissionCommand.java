package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** Administrator input for atomically admitting one student. */
public record CreateStudentAdmissionCommand(String studentName, String gender,
        String email, String phone, String majorId, String classId,
        int enrollmentYear, StudentType studentType) implements Serializable { }
