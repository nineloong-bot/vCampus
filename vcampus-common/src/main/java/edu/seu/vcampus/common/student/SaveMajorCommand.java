package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** Creates or updates a major in one department. */
public record SaveMajorCommand(String majorId, String departmentId, String code, String name,
        String grades, boolean active, long expectedVersion) implements Serializable { }
