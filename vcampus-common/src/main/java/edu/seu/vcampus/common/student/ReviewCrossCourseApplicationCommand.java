package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** Command for the offering college admin to approve or reject a cross-course application. */
/**
 * Carries immutable review cross course application command data.
 * @param applicationId the application identifier
 * @param approved the approved
 * @param allocatedQuota the allocated quota
 * @param reviewComment the review comment
 */
public record ReviewCrossCourseApplicationCommand(
        String applicationId,
        boolean approved,
        Integer allocatedQuota,
        String reviewComment) implements Serializable { }
