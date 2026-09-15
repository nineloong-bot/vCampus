package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** Query for listing cross-disciplinary course applications. */
public record CrossCourseApplicationQuery(
        String offeringDepartmentId,
        String targetDepartmentId,
        CrossCourseApplicationStatus status) implements Serializable { }
