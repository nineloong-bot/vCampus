package edu.seu.vcampus.common.student;

import java.io.Serializable;
import java.time.LocalDate;

/** Complete student profile returned to authorized clients. */
public record StudentView(String studentId, String userId, String campusCardNumber,
        String studentNumber, StudentType studentType, String studentName, String gender,
        String email, String phone, String majorId, String classId,
        LocalDate enrollmentDate, StudentStatus status, long rowVersion,
        String departmentName, String majorName, String className)
        implements Serializable { }
