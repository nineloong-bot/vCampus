package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** Creates or updates a major in one department. */
/**
 * Carries immutable save major command data.
 * @param majorId the major identifier
 * @param departmentId the department identifier
 * @param code the code
 * @param name the name
 * @param grades the grades
 * @param active the active
 * @param expectedVersion the expected version
 */
public record SaveMajorCommand(String majorId, String departmentId, String code, String name,
        String grades, boolean active, long expectedVersion) implements Serializable { }
