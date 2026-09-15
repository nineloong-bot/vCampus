package edu.seu.vcampus.common.course;
import java.io.*; import java.math.BigDecimal; import java.time.Instant;
/** Serializable catalog course. */
/**
 * Carries immutable course view data.
 * @param courseId the course identifier
 * @param courseCode the course code
 * @param courseName the course name
 * @param credit the credit
 * @param totalHours the total hours
 * @param description the description
 * @param active the active
 * @param rowVersion the row version
 * @param createdAt the created at
 * @param updatedAt the updated at
 */
public record CourseView(String courseId,String courseCode,String courseName,BigDecimal credit,int totalHours,String description,boolean active,long rowVersion,Instant createdAt,Instant updatedAt) implements Serializable { @Serial private static final long serialVersionUID=1L; }
