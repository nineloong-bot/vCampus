package edu.seu.vcampus.common.student;
import java.io.Serializable;
public record StudentSearchQuery(String keyword, String departmentId, String majorId,
        String classId, StudentStatus status, int page, int pageSize) implements Serializable { }
