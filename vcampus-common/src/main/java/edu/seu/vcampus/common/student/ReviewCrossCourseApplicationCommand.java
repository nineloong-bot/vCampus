package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** Command for the offering college admin to approve or reject a cross-course application. */
public record ReviewCrossCourseApplicationCommand(
        String applicationId,
        boolean approved,
        Integer allocatedQuota,
        String reviewComment) implements Serializable { }
