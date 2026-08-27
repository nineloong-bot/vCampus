package edu.seu.vcampus.common.student;
import java.io.Serializable;
public record StudentIdentity(String studentId, String userId, String campusCardNumber,
        String studentNumber, StudentType studentType, String majorId, String classId,
        StudentStatus status) implements Serializable { }
