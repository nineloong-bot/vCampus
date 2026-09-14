package edu.seu.vcampus.common.student;
import java.io.Serializable;
/**
 * 学生基础档案摘要视图对象。
 */
public record StudentSummary(String studentId, String campusCardNumber, String studentNumber,
        String studentName, String majorId, String classId, StudentStatus status)
        implements Serializable { }
