package edu.seu.vcampus.common.student;
import java.io.Serializable;
public record ClassView(String classId, String majorId, String code, String name,
        int enrollmentYear, int classNumber, boolean active, long rowVersion) implements Serializable {
    @Override public String toString() { return name; }
}
