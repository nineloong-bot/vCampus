package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** Query for listing cross-disciplinary course applications. */
/**
 * Carries immutable cross course application query data.
 * @param offeringDepartmentId the offering department identifier
 * @param targetDepartmentId the target department identifier
 * @param status the status
 */
public record CrossCourseApplicationQuery(
        String offeringDepartmentId,
        String targetDepartmentId,
        CrossCourseApplicationStatus status) implements Serializable { }
