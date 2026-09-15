package edu.seu.vcampus.common.course;
import java.io.*; import java.math.BigDecimal; import java.time.Instant;
/** Serializable catalog course including its owning college. */
public record CourseView(String courseId, String courseCode, String courseName,
        String departmentId, String departmentName, BigDecimal credit, int totalHours,
        String description, boolean active, long rowVersion, Instant createdAt,
        Instant updatedAt) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    /** Compatibility constructor for callers without college metadata. */
    public CourseView(String courseId, String courseCode, String courseName, BigDecimal credit,
            int totalHours, String description, boolean active, long rowVersion,
            Instant createdAt, Instant updatedAt) {
        this(courseId, courseCode, courseName, null, null, credit, totalHours, description,
                active, rowVersion, createdAt, updatedAt);
    }
}
