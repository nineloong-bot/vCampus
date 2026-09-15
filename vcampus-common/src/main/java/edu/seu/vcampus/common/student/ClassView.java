package edu.seu.vcampus.common.student;
import java.io.Serializable;
/**
 * Carries immutable class view data.
 * @param classId the class identifier
 * @param majorId the major identifier
 * @param code the code
 * @param name the name
 * @param enrollmentYear the enrollment year
 * @param classNumber the class number
 * @param active the active
 * @param rowVersion the row version
 */
public record ClassView(String classId, String majorId, String code, String name,
        int enrollmentYear, int classNumber, boolean active, long rowVersion) implements Serializable {
    @Override public String toString() { return name; }
}
