package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** Creates or updates a class and its numbering metadata. */
/**
 * Carries immutable save class command data.
 * @param classId the class identifier
 * @param majorId the major identifier
 * @param code the code
 * @param name the name
 * @param enrollmentYear the enrollment year
 * @param classNumber the class number
 * @param active the active
 * @param expectedVersion the expected version
 */
public record SaveClassCommand(String classId, String majorId, String code, String name,
        int enrollmentYear, int classNumber, boolean active, long expectedVersion)
        implements Serializable { }
