package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** Query for searching the university-wide course pool. */
public record CoursePoolQuery(
        String departmentId,
        String keyword) implements Serializable { }
