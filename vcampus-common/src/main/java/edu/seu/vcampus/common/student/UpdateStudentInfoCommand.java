package edu.seu.vcampus.common.student;
import java.io.Serializable;
import java.time.LocalDate;
public record UpdateStudentInfoCommand(String studentId, String studentNumber, String classId,
        StudentStatus status, LocalDate effectiveDate, String reason, long expectedVersion)
        implements Serializable { }
