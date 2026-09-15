package edu.seu.vcampus.common.student;
import java.io.Serializable;
/**
 * Carries immutable student search query data.
 * @param keyword the keyword
 * @param departmentId the department identifier
 * @param majorId the major identifier
 * @param classId the class identifier
 * @param status the status
 * @param page the page
 * @param pageSize the page size
 */
public record StudentSearchQuery(String keyword, String departmentId, String majorId,
        String classId, StudentStatus status, int page, int pageSize) implements Serializable { }
