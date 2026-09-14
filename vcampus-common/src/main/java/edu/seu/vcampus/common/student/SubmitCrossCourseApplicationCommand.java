package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** Command to submit an application to introduce a course from another college. */
public record SubmitCrossCourseApplicationCommand(
        String courseId,
        String targetPlanId,
        int semester,
        int requestedQuota,
        String reason) implements Serializable { }
