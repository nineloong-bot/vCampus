package edu.seu.vcampus.common.course;
import java.io.*; import java.math.BigDecimal; import java.util.Objects;
/** Optimistically updates a catalog course. */
/**
 * Carries immutable update course command data.
 * @param courseId the course identifier
 * @param courseCode the course code
 * @param courseName the course name
 * @param credit the credit
 * @param totalHours the total hours
 * @param description the description
 * @param active the active
 * @param expectedVersion the expected version
 */
public record UpdateCourseCommand(String courseId,String courseCode,String courseName,BigDecimal credit,int totalHours,String description,boolean active,long expectedVersion) implements Serializable { @Serial private static final long serialVersionUID=1L; /**
 * Validates and creates a update course command.
 * @param courseId the course identifier
 * @param courseCode the course code
 * @param courseName the course name
 * @param credit the credit
 * @param totalHours the total hours
 * @param description the description
 * @param active the active
 * @param expectedVersion the expected version
 */
public UpdateCourseCommand { Objects.requireNonNull(courseId);Objects.requireNonNull(courseCode);Objects.requireNonNull(courseName);Objects.requireNonNull(credit);CourseValidation.text("courseId",courseId,36);CourseValidation.text("courseCode",courseCode,24);CourseValidation.text("courseName",courseName,128);if(credit.signum()<=0||totalHours<=0||expectedVersion<0)throw new IllegalArgumentException("invalid course"); } }
