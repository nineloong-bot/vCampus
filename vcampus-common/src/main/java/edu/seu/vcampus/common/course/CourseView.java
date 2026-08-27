package edu.seu.vcampus.common.course;
import java.io.*; import java.math.BigDecimal; import java.time.Instant;
/** Serializable catalog course. */
public record CourseView(String courseId,String courseCode,String courseName,BigDecimal credit,int totalHours,String description,boolean active,long rowVersion,Instant createdAt,Instant updatedAt) implements Serializable { @Serial private static final long serialVersionUID=1L; }
