package edu.seu.vcampus.common.student;
import java.io.Serializable;
/**
 * 学籍档案综合检索查询参数对象。
 */
public record StudentSearchQuery(String keyword, String departmentId, String majorId,
        String classId, StudentStatus status, int page, int pageSize) implements Serializable { }
