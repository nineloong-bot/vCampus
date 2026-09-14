package edu.seu.vcampus.common.student;
import java.io.Serializable;
import java.time.LocalDate;
/**
 * 学籍异动与学制信息更新命令。
 */
public record UpdateStudentEnrollmentCommand(String studentId, String classId,
        LocalDate effectiveDate, String reason, long expectedVersion) implements Serializable { }
