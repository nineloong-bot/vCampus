package edu.seu.vcampus.common.student;
import java.io.Serializable;
/**
 * Carries immutable major view data.
 * @param majorId the major identifier
 * @param departmentId the department identifier
 * @param code the code
 * @param name the name
 * @param grades the grades
 * @param active the active
 * @param rowVersion the row version
 */
public record MajorView(String majorId, String departmentId, String code, String name,
        String grades, boolean active, long rowVersion) implements Serializable {
    @Override public String toString() { return name; }
}
