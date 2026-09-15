package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** Query for searching the university-wide course pool. */
/**
 * Carries immutable course pool query data.
 * @param departmentId the department identifier
 * @param keyword the keyword
 */
public record CoursePoolQuery(
        String departmentId,
        String keyword) implements Serializable { }
