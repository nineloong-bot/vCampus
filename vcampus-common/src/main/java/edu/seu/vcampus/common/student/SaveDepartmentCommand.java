package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** Creates a department when the id is blank, otherwise updates it optimistically. */
public record SaveDepartmentCommand(String departmentId, String code, String name,
        boolean active, long expectedVersion) implements Serializable { }
