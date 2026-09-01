package edu.seu.vcampus.common.student;
import java.io.Serializable;
public record StudentSummary(String studentId, String campusCardNumber, String studentNumber,
        String studentName, String majorId, String classId, StudentStatus status)
        implements Serializable { }
