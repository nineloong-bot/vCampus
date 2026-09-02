package edu.seu.vcampus.common.student;
import java.io.Serializable;
public record MajorView(String majorId, String departmentId, String code, String name,
        boolean active, long rowVersion) implements Serializable {
    @Override public String toString() { return name; }
}
