package edu.seu.vcampus.common.student;
import java.io.Serializable;
import java.time.LocalDate;
/**
 * 变更学生在籍状态的请求命令。
 *  *
 *  * @param studentId 学生标识
 *  * @param targetStatus 目标学籍状态
 *  * @param reason 状态变更原因
 */
public record ChangeStudentStatusCommand(String studentId, StudentStatus status,
        LocalDate effectiveDate, String reason, long expectedVersion) implements Serializable { }
