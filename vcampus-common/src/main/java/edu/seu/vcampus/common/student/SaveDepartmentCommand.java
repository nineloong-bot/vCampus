package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** Creates a department when the id is blank, otherwise updates it optimistically. */
/**
 * Carries immutable save department command data.
 * @param departmentId the department identifier
 * @param code the code
 * @param name the name
 * @param active the active
 * @param expectedVersion the expected version
 */
public record SaveDepartmentCommand(String departmentId, String code, String name,
        boolean active, long expectedVersion) implements Serializable { }
