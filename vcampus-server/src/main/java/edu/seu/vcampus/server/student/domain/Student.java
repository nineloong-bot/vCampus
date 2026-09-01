package edu.seu.vcampus.server.student.domain;

import edu.seu.vcampus.common.student.StudentStatus;
import edu.seu.vcampus.common.student.StudentType;

import java.time.Instant;
import java.time.LocalDate;

/** Persistence model for a student profile; campus card remains owned by the user module. */
public record Student(String studentId, String userId, String studentNumber,
        StudentType studentType, String studentName, String gender, String email,
        String phone, String majorId, String classId, LocalDate enrollmentDate,
        StudentStatus status, long rowVersion, Instant createdAt, Instant updatedAt) { }
