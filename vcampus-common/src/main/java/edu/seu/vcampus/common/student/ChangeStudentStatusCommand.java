package edu.seu.vcampus.common.student;
import java.io.Serializable;
import java.time.LocalDate;
public record ChangeStudentStatusCommand(String studentId, StudentStatus status,
        LocalDate effectiveDate, String reason, long expectedVersion) implements Serializable { }
