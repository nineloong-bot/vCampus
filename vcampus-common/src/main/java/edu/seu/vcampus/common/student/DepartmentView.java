package edu.seu.vcampus.common.student;
import java.io.Serializable;
/**
 * Carries immutable department view data.
 * @param departmentId the department identifier
 * @param code the code
 * @param name the name
 * @param active the active
 * @param rowVersion the row version
 */
public record DepartmentView(String departmentId, String code, String name,
        boolean active, long rowVersion) implements Serializable {
    @Override public String toString() { return name; }
}
