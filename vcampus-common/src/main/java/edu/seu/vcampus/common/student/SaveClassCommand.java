package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** Creates or updates a class and its numbering metadata. */
public record SaveClassCommand(String classId, String majorId, String code, String name,
        int enrollmentYear, int classNumber, boolean active, long expectedVersion)
        implements Serializable { }
