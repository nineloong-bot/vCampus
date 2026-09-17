package edu.seu.vcampus.common.course;

import java.io.Serializable;

/** Stable owning-college option exposed by canonical curriculum data. */
public record CourseDepartmentOption(String departmentId, String departmentName)
        implements Serializable {
    /** Validates the durable identifier and visible name. */
    public CourseDepartmentOption {
        if (departmentId == null || departmentId.isBlank()) throw new IllegalArgumentException("departmentId");
        if (departmentName == null || departmentName.isBlank()) throw new IllegalArgumentException("departmentName");
    }
}
