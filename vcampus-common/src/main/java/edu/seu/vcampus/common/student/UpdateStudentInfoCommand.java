package edu.seu.vcampus.common.student;
import java.io.Serializable;
import java.time.LocalDate;
/**
 * 管理员更新学生个人档案信息的请求命令。
 */
public record UpdateStudentInfoCommand(String studentId, String studentNumber, String classId,
        StudentStatus status, LocalDate effectiveDate, String reason, long expectedVersion)
        implements Serializable { }
