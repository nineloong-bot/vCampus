package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** Command to submit an application to introduce a course from another college. */
/**
 * Carries immutable submit cross course application command data.
 * @param courseId the course identifier
 * @param targetPlanId the target plan identifier
 * @param semester the semester
 * @param requestedQuota the requested quota
 * @param reason the reason
 */
public record SubmitCrossCourseApplicationCommand(
        String courseId,
        String targetPlanId,
        int semester,
        int requestedQuota,
        String reason) implements Serializable { }
