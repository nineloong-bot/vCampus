package edu.seu.vcampus.common.student;
import java.io.Serializable;
import java.time.LocalDate;
public record UpdateStudentEnrollmentCommand(String studentId, String classId,
        LocalDate effectiveDate, String reason, long expectedVersion) implements Serializable { }
